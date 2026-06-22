// @source t32remote:internal/server/handlers_run.go
// @lines 44-130
// @note ExecuteCommand → runWithProgress: broker 구독 + worker goroutine + 이벤트 포워딩
// @synced 2026-06-22T22:22:10.912Z

func (s *Server) ExecuteCommand(req *v1.ExecuteCommandRequest, stream v1.T32Remote_ExecuteCommandServer) error {
	if !s.sess.IsAttached() {
		return fmt.Errorf("not attached")
	}
	log.Printf("ExecuteCommand: cmd=%q", req.Command)
	return s.runWithProgress(stream.Context(), stream, s.sess.Worker(), req.Command, false)
}

// progressStream is the common interface of all server-streaming RPCs that
// emit ProgressEvent.
type progressStream interface {
	Send(*v1.ProgressEvent) error
}

// runWithProgress: subscribe to broker, fire the command via worker (in a
// goroutine so we can also stream events that the command itself triggers),
// forward events until completion or context cancel.
func (s *Server) runWithProgress(ctx context.Context, stream progressStream, w *t32.Worker, cmd string, waitIdle bool) error {
	events, unsub := s.broker.Subscribe(64)
	defer unsub()

	cmdDone := make(chan error, 1)
	go func() {
		cmdDone <- w.Cmd(ctx, cmd)
	}()

	// Track ENTERED dialog -> apply policy if matching.
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case ev := <-events:
			if ev == nil {
				continue
			}
			if dlg := ev.GetDialog(); dlg != nil {
				if r := s.policy.Match(dlg.Header); r != nil {
					// Best-effort apply; ignore error (will surface via Area).
					go func(rule *v1.DialogRule) {
						applyCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
						defer cancel()
						_ = applyRule(applyCtx, w, rule)
					}(r)
				}
			}
			if err := stream.Send(ev); err != nil {
				return err
			}
		case err := <-cmdDone:
			if err != nil {
				_ = stream.Send(&v1.ProgressEvent{
					Event:    &v1.ProgressEvent_Err{Err: &v1.ErrorEvent{Message: err.Error()}},
					TsUnixMs: time.Now().UnixMilli(),
				})
				return err
			}
			// T32_Cmd returns as soon as PRACTICE *accepts* the command. For a
			// DO script that is long before the script finishes, so wait for
			// PRACTICE to go idle (best effort) while still forwarding events.
			if waitIdle {
				if err := s.waitPracticeIdle(ctx, stream, w, events); err != nil {
					return err
				}
			}
			// Drain any late events the poller is about to publish. Short
			// PRACTICE commands (e.g. `PRINT "hi"`) finish before the next
			// 150ms poll tick, so without this grace window the stream
			// closes before AREA messages arrive.
			drainUntil := time.After(300 * time.Millisecond)
		drain:
			for {
				select {
				case ev := <-events:
					if ev == nil {
						continue
					}
					_ = stream.Send(ev)
				case <-drainUntil:
					break drain
				case <-ctx.Done():
					break drain
				}
			}
			_ = stream.Send(&v1.ProgressEvent{
				Event:    &v1.ProgressEvent_Done{Done: &v1.Completed{Message: "ok"}},
				TsUnixMs: time.Now().UnixMilli(),
			})
