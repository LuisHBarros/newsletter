package metrics

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"go.uber.org/zap"
)

const Namespace = "Assine/Notifications"

type Unit string

const (
	Count        Unit = "Count"
	Milliseconds Unit = "Milliseconds"
	None         Unit = "None"
)

type MetricDef struct {
	Name string
	Unit Unit
}

type Emitter struct {
	logger *zap.Logger
	mu     sync.Mutex
	buf    []byte
}

func NewEmitter(logger *zap.Logger) *Emitter {
	return &Emitter{logger: logger}
}

func (e *Emitter) Emit(dimensions []string, values map[string]interface{}, defs []MetricDef) {
	now := time.Now().UnixMilli()

	emf := map[string]interface{}{
		"_aws": map[string]interface{}{
			"Timestamp": now,
			"CloudWatchMetrics": []map[string]interface{}{
				{
					"Namespace":  Namespace,
					"Dimensions": []string{"Service"},
					"Metrics":    defs,
				},
			},
		},
	}

	if len(dimensions) > 0 {
		dims := make([]string, 0, len(dimensions)+1)
		dims = append(dims, "Service")
		dims = append(dims, dimensions...)
		emf["_aws"].(map[string]interface{})["CloudWatchMetrics"].([]map[string]interface{})[0]["Dimensions"] = [][]string{dims}
	}

	emf["Service"] = "notifications"
	for k, v := range values {
		emf[k] = v
	}

	e.mu.Lock()
	if e.buf == nil {
		e.buf = make([]byte, 0, 1024)
	}
	out, err := json.Marshal(emf)
	if err != nil {
		e.mu.Unlock()
		e.logger.Error("emf marshal failed", zap.Error(err))
		return
	}
	e.buf = append(e.buf[:0], out...)
	e.mu.Unlock()

	fmt.Println(string(e.buf))
}

func RecordEmailSent(logger *zap.Logger, eventType string) {
	EmitCount(logger, "EmailSent", eventType, 1)
}

func RecordEmailFailed(logger *zap.Logger, eventType string) {
	EmitCount(logger, "EmailFailed", eventType, 1)
}

func RecordFanOutSubscribers(logger *zap.Logger, eventType string, count int) {
	e := NewEmitter(logger)
	e.Emit(
		[]string{"EventType"},
		map[string]interface{}{
			"EventType":         eventType,
			"FanOutSubscribers": count,
		},
		[]MetricDef{
			{Name: "FanOutSubscribers", Unit: Count},
		},
	)
}

func RecordProcessingDuration(logger *zap.Logger, eventType string, durationMs float64) {
	e := NewEmitter(logger)
	e.Emit(
		[]string{"EventType"},
		map[string]interface{}{
			"EventType":          eventType,
			"ProcessingDuration": durationMs,
		},
		[]MetricDef{
			{Name: "ProcessingDuration", Unit: Milliseconds},
		},
	)
}

func EmitCount(logger *zap.Logger, metricName, eventType string, value float64) {
	e := NewEmitter(logger)
	e.Emit(
		[]string{"EventType"},
		map[string]interface{}{
			"EventType": eventType,
			metricName:  value,
		},
		[]MetricDef{
			{Name: metricName, Unit: Count},
		},
	)
}

func RecordBatchItemFailure(logger *zap.Logger, reason string) {
	e := NewEmitter(logger)
	e.Emit(
		[]string{"Reason"},
		map[string]interface{}{
			"Reason":           reason,
			"BatchItemFailure": 1,
		},
		[]MetricDef{
			{Name: "BatchItemFailure", Unit: Count},
		},
	)
}
