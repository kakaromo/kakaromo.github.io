// @source t32remote:cmd/t32remote/main.go
// @lines 1-48
// @note 서버 진입점 — :50551 listen + graceful shutdown, cgo(Windows)/stub(host) 백엔드
// @synced 2026-06-22T22:22:10.912Z

// Command t32remote runs a gRPC server that controls a local TRACE32 instance
// via the Lauterbach Remote API.
package main

import (
	"context"
	"flag"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"

	v1 "t32remote/api/v1"
	"t32remote/internal/server"
	"t32remote/internal/t32"
	"google.golang.org/grpc"
)

func main() {
	addr := flag.String("addr", ":50551", "gRPC listen address")
	flag.Parse()

	lis, err := net.Listen("tcp", *addr)
	if err != nil {
		log.Fatalf("listen %s: %v", *addr, err)
	}

	api := t32.New()
	srv := server.New(api)

	g := grpc.NewServer()
	v1.RegisterT32RemoteServer(g, srv)

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	go func() {
		<-ctx.Done()
		log.Println("shutting down")
		g.GracefulStop()
	}()

	log.Printf("t32remote listening on %s", *addr)
	if err := g.Serve(lis); err != nil {
		log.Fatalf("serve: %v", err)
	}
}
