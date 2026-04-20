terraform {
  backend "s3" {
    bucket         = "assine-tfstate"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "assine-tfstate-lock"
    encrypt        = true
  }
}
