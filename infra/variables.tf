variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.small"
}

variable "key_name" {
  description = "EC2 키페어 이름 (AWS 콘솔에서 생성한 키페어)"
  type        = string
}

variable "volume_size" {
  description = "EBS 볼륨 크기 (GB)"
  type        = number
  default     = 20
}

variable "domain_name" {
  description = "도메인 이름 (예: fairbid.com)"
  type        = string
}

variable "my_ip" {
  description = "SSH 접근을 허용할 IP (CIDR 형식, 예: 123.456.789.0/32)"
  type        = string
}

variable "public_key" {
  description = "EC2 SSH 접속용 public key (ssh-keygen -y -f key.pem 으로 추출)"
  type        = string
}

# =============================================================================
# 스케일아웃 변수
# =============================================================================
variable "app_ami_id" {
  description = "App 서버용 AMI ID (Docker + 코드가 포함된 골든 이미지)"
  type        = string
}

variable "asg_min_size" {
  description = "ASG 최소 인스턴스 수"
  type        = number
  default     = 1
}

variable "asg_max_size" {
  description = "ASG 최대 인스턴스 수"
  type        = number
  default     = 4
}

variable "asg_desired_capacity" {
  description = "REST ASG 초기 인스턴스 수"
  type        = number
  default     = 1
}

# =============================================================================
# WebSocket ASG 변수
# =============================================================================
variable "ws_asg_min_size" {
  description = "WebSocket ASG 최소 인스턴스 수"
  type        = number
  default     = 1
}

variable "ws_asg_max_size" {
  description = "WebSocket ASG 최대 인스턴스 수"
  type        = number
  default     = 4
}

variable "ws_asg_desired_capacity" {
  description = "WebSocket ASG 초기 인스턴스 수"
  type        = number
  default     = 1
}
