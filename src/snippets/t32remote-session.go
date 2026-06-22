// @source t32remote:internal/session/session.go
// @lines 25-133
// @note Session — idempotent Attach(Ping 먼저, 이미 붙어있으면 Attach 생략) + MarkDisconnected
// @synced 2026-06-22T22:22:10.912Z

type Session struct {
	mu       sync.Mutex
	api      t32.API
	worker   *t32.Worker
	attached bool
}

func New(api t32.API) *Session {
	return &Session{api: api}
}

// Worker is the serialized executor (created on Attach).
func (s *Session) Worker() *t32.Worker {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.worker
}

func (s *Session) IsAttached() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.attached
}

// Attach is idempotent: if already attached it returns nil so callers can
// safely attach before every operation without tracking connection state.
func (s *Session) Attach(ctx context.Context, node string, port, packLen int) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.attached {
		return nil
	}
	dev := devIDFromEnv()
	log.Printf("session: Config(node=%s port=%d packLen=%d)", node, port, packLen)
	if err := s.api.Config(node, port, packLen); err != nil {
		log.Printf("session: Config failed: %v", err)
		return err
	}
	// Init, retrying once with an Exit only if it fails. Calling Exit
	// unconditionally before Init tears down an already-active RCL link, which
	// makes PowerView throw "fatal error #FF" when re-initializing a live
	// connection. So try Init first; only if it fails (e.g. rc=-1 from leftover
	// socket state) do we Exit to clear it and retry.
	log.Printf("session: Init")
	if err := s.api.Init(); err != nil {
		log.Printf("session: Init failed (%v) — Exit and retry", err)
		_ = s.api.Exit()
		if err2 := s.api.Init(); err2 != nil {
			log.Printf("session: Init retry failed: %v", err2)
			return err2
		}
	}
	// Ping first: if PowerView is already connected over RCL it answers (this is
	// the "remote ping received" line in PowerView). In that case skip
	// T32_Attach — calling it again retriggers a USB connect ("try USB connect")
	// on an already-attached debugger, which throws "fatal error #FF". Only call
	// T32_Attach when Ping fails (no live connection yet).
	log.Printf("session: Ping")
	if err := s.api.Ping(); err != nil {
		log.Printf("session: Ping failed (%v) — calling Attach(dev=%d)", err, dev)
		if err := s.api.Attach(dev); err != nil {
			log.Printf("session: Attach failed: %v", err)
			return err
		}
		if err := s.api.Ping(); err != nil {
			log.Printf("session: Ping after Attach failed: %v", err)
			return err
		}
	} else {
		log.Printf("session: already connected (ping OK) — skipping Attach")
	}
	log.Printf("session: attach OK, starting worker")
	s.worker = t32.NewWorker(context.Background(), s.api)
	s.attached = true
	return nil
}

func (s *Session) Detach() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.attached {
		return nil
	}
	err := s.api.Exit()
	s.attached = false
	s.worker = nil
	return err
}

// MarkDisconnected drops the attached state when the poller detects the
// TRACE32 instance has gone away (e.g. PowerView was closed), so subsequent
// requests are rejected with "not attached" and the client must re-attach.
//
// It calls T32_Exit best-effort: even though the peer is gone, the local
// t32api must release its socket/connection state, otherwise the next
// Config/Init/Attach fails and re-attach is impossible. The error is ignored.
// Idempotent.
func (s *Session) MarkDisconnected() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.attached {
		return
	}
	if err := s.api.Exit(); err != nil {
		log.Printf("session: Exit on disconnect failed (ignored): %v", err)
	}
	s.attached = false
	s.worker = nil
}
