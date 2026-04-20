output "queues" {
  value = { for k, v in aws_sqs_queue.main : k => {
    url     = v.id
    arn     = v.arn
    dlq_arn = aws_sqs_queue.dlq[k].arn
  } }
}
