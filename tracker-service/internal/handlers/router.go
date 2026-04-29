package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func RegisterRoutes(router *gin.Engine, trackingHandler *TrackingHandler) {
	router.GET("/healthz", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})

	router.GET("/track/open", trackingHandler.TrackOpen)
	router.GET("/track/click", trackingHandler.TrackClick)
	router.POST("/track/submit", trackingHandler.TrackSubmit)
}
