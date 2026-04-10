package handler

import "fmt"

type ValidateOrderHandler struct{}

func (h *ValidateOrderHandler) Execute(payload string) (string, error) {
	fmt.Printf("[validate-order] Processing payload: %s\n", payload)
	return `{"valid": true}`, nil
}

type ChargePaymentHandler struct{}

func (h *ChargePaymentHandler) Execute(payload string) (string, error) {
	fmt.Printf("[charge-payment] Processing payload: %s\n", payload)
	return `{"charged": true}`, nil
}

type SendNotificationHandler struct{}

func (h *SendNotificationHandler) Execute(payload string) (string, error) {
	fmt.Printf("[send-notification] Processing payload: %s\n", payload)
	return `{"sent": true}`, nil
}
