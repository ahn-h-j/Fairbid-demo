output "infra_server_public_ip" {
  description = "인프라 서버 Elastic IP"
  value       = aws_eip.fairbid.public_ip
}

output "infra_server_private_ip" {
  description = "인프라 서버 Private IP (App 서버가 연결할 주소)"
  value       = aws_instance.fairbid.private_ip
}

output "infra_server_instance_id" {
  description = "인프라 서버 인스턴스 ID"
  value       = aws_instance.fairbid.id
}

output "alb_dns_name" {
  description = "ALB DNS (App 서버 접근 엔드포인트)"
  value       = aws_lb.app.dns_name
}

output "asg_name" {
  description = "Auto Scaling Group 이름"
  value       = aws_autoscaling_group.app.name
}

output "nameservers" {
  description = "Route 53 네임서버 목록 (가비아에 이 값들을 입력)"
  value       = aws_route53_zone.fairbid.name_servers
}

output "domain_name" {
  description = "설정된 도메인"
  value       = var.domain_name
}
