package main

import (
	"net/http"
	"os"
	"time"
)

func main() {
	url := "http://127.0.0.1:8081/healthz"
	if len(os.Args) > 1 && os.Args[1] != "" {
		url = os.Args[1]
	}

	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		os.Exit(1)
	}
	defer resp.Body.Close()

	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusBadRequest {
		os.Exit(1)
	}
}
