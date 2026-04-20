package tracing

import (
	"context"

	"github.com/aws/aws-xray-sdk-go/xray"
)

func BeginSubsegment(ctx context.Context, name string) (context.Context, func()) {
	seg := xray.GetSegment(ctx)
	if seg == nil || !seg.Sampled {
		return ctx, func() {}
	}

	ctx, sub := xray.BeginSubsegment(ctx, name)
	return ctx, func() {
		sub.Close(nil)
	}
}
