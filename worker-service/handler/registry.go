package handler

import "fmt"

type TaskPayload struct {
	TaskID      string `json:"taskId"`
	WorkflowID  string `json:"workflowId"`
	HandlerName string `json:"handlerName"`
	Payload     string `json:"payload"`
}

type TaskResult struct {
	TaskID  string `json:"taskId"`
	Status  string `json:"status"`
	Message string `json:"message"`
}

type Handler interface {
	Execute(payload string) (string, error)
}

type Registry struct {
	handlers map[string]Handler
}

func NewRegistry() *Registry {
	r := &Registry{
		handlers: make(map[string]Handler),
	}
	r.Register("validate-order", &ValidateOrderHandler{})
	r.Register("charge-payment", &ChargePaymentHandler{})
	r.Register("send-notification", &SendNotificationHandler{})
	return r
}

func (r *Registry) Register(name string, h Handler) {
	r.handlers[name] = h
}

func (r *Registry) Get(name string) (Handler, error) {
	h, ok := r.handlers[name]
	if !ok {
		return nil, fmt.Errorf("handler not found: %s", name)
	}
	return h, nil
}
