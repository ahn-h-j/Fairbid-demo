# =============================================================================
# 최신 Ubuntu 24.04 AMI 조회
# =============================================================================
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# =============================================================================
# Key Pair (로컬 .pem 파일의 public key를 AWS에 등록)
# =============================================================================
resource "aws_key_pair" "fairbid" {
  key_name   = var.key_name
  public_key = var.public_key
}

# =============================================================================
# Security Group
# =============================================================================
resource "aws_security_group" "fairbid" {
  name        = "fairbid-sg"
  description = "FairBid EC2 Security Group"

  # SSH - CD 파이프라인(GitHub Actions)에서 접근 필요하므로 전체 허용
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # HTTP
  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # HTTPS
  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Spring Boot API (디버깅용, 본인 IP만 허용)
  ingress {
    description = "Spring Boot API"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.my_ip]
  }

  # Outbound 전체 허용
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "fairbid-sg"
  }
}

# =============================================================================
# EC2 Instance
# =============================================================================
resource "aws_instance" "fairbid" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.fairbid.key_name
  vpc_security_group_ids = [aws_security_group.fairbid.id]

  root_block_device {
    volume_size = var.volume_size
    volume_type = "gp3"
  }

  user_data = templatefile("${path.module}/user-data.sh", {
    domain_name = var.domain_name
  })

  tags = {
    Name = "fairbid-infra"
  }

  # AMI 업데이트로 인한 인스턴스 교체 방지 (인프라 서버는 stateful)
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# =============================================================================
# Elastic IP (재시작 시 IP 변경 방지)
# =============================================================================
resource "aws_eip" "fairbid" {
  instance = aws_instance.fairbid.id
  domain   = "vpc"

  tags = {
    Name = "fairbid-eip"
  }
}

# =============================================================================
# Route 53 Hosted Zone
# =============================================================================
resource "aws_route53_zone" "fairbid" {
  name = var.domain_name

  # destroy해도 Hosted Zone은 유지 (네임서버 변경 방지)
  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name = "fairbid-zone"
  }
}

# 루트 도메인 → Elastic IP
resource "aws_route53_record" "root" {
  zone_id = aws_route53_zone.fairbid.zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = 300
  records = [aws_eip.fairbid.public_ip]
}

# www 서브도메인 → Elastic IP
resource "aws_route53_record" "www" {
  zone_id = aws_route53_zone.fairbid.zone_id
  name    = "www.${var.domain_name}"
  type    = "A"
  ttl     = 300
  records = [aws_eip.fairbid.public_ip]
}

# api 서브도메인 → Elastic IP
resource "aws_route53_record" "api" {
  zone_id = aws_route53_zone.fairbid.zone_id
  name    = "api.${var.domain_name}"
  type    = "A"
  ttl     = 300
  records = [aws_eip.fairbid.public_ip]
}
