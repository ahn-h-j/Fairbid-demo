# =============================================================================
# 스케일아웃 인프라 (ALB + ASG + Launch Template)
# =============================================================================

# VPC 데이터 (기본 VPC 사용)
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "availability-zone"
    values = ["ap-northeast-2a", "ap-northeast-2c"]
  }
}

# =============================================================================
# ALB Security Group
# =============================================================================
resource "aws_security_group" "alb" {
  name        = "fairbid-alb-sg"
  description = "FairBid ALB Security Group"
  vpc_id      = data.aws_vpc.default.id

  # HTTP
  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Backend (8080) - ALB가 직접 App 서버로 프록시
  ingress {
    description = "Backend API"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "fairbid-alb-sg"
  }
}

# =============================================================================
# fairbid-sg에 ALB로부터의 8080 인바운드 추가
# =============================================================================
resource "aws_security_group_rule" "app_from_alb" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
  security_group_id        = aws_security_group.fairbid.id
  description              = "App from ALB"
}

# fairbid-sg: VPC 내부에서 MySQL 접근
resource "aws_security_group_rule" "mysql_from_vpc" {
  type              = "ingress"
  from_port         = 3306
  to_port           = 3306
  protocol          = "tcp"
  cidr_blocks       = ["172.31.0.0/16"]
  security_group_id = aws_security_group.fairbid.id
  description       = "MySQL from VPC"
}

# fairbid-sg: VPC 내부에서 Redis 접근
resource "aws_security_group_rule" "redis_from_vpc" {
  type              = "ingress"
  from_port         = 6379
  to_port           = 6381
  protocol          = "tcp"
  cidr_blocks       = ["172.31.0.0/16"]
  security_group_id = aws_security_group.fairbid.id
  description       = "Redis from VPC"
}

# fairbid-sg: VPC 내부에서 Sentinel 접근
resource "aws_security_group_rule" "sentinel_from_vpc" {
  type              = "ingress"
  from_port         = 26379
  to_port           = 26381
  protocol          = "tcp"
  cidr_blocks       = ["172.31.0.0/16"]
  security_group_id = aws_security_group.fairbid.id
  description       = "Redis Sentinel from VPC"
}

# fairbid-sg: Grafana
resource "aws_security_group_rule" "grafana" {
  type              = "ingress"
  from_port         = 3001
  to_port           = 3001
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.fairbid.id
  description       = "Grafana"
}

# fairbid-sg: Prometheus
resource "aws_security_group_rule" "prometheus" {
  type              = "ingress"
  from_port         = 9595
  to_port           = 9595
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.fairbid.id
  description       = "Prometheus"
}

# =============================================================================
# Application Load Balancer
# =============================================================================
resource "aws_lb" "app" {
  name               = "fairbid-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = data.aws_subnets.default.ids

  tags = {
    Name = "fairbid-alb"
  }
}

# =============================================================================
# Target Group
# =============================================================================
resource "aws_lb_target_group" "app" {
  name     = "fairbid-app-tg"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = data.aws_vpc.default.id

  health_check {
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  tags = {
    Name = "fairbid-app-tg"
  }
}

# =============================================================================
# ALB Listener (HTTP:80 → Target Group)
# =============================================================================
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# =============================================================================
# Launch Template (App 서버용)
# =============================================================================
resource "aws_launch_template" "app" {
  name          = "fairbid-app-lt"
  image_id      = var.app_ami_id
  instance_type = var.instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [aws_security_group.fairbid.id]

  user_data = base64encode(templatefile("${path.module}/user-data-app.sh", {
    infra_private_ip = aws_instance.fairbid.private_ip
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "fairbid-app"
    }
  }

  # 새 버전이 만들어져도 기존 인스턴스에 영향 없음
  lifecycle {
    create_before_destroy = true
  }
}

# =============================================================================
# Auto Scaling Group
# =============================================================================
resource "aws_autoscaling_group" "app" {
  name                = "fairbid-app-asg"
  min_size            = var.asg_min_size
  max_size            = var.asg_max_size
  desired_capacity    = var.asg_desired_capacity
  vpc_zone_identifier = data.aws_subnets.default.ids
  target_group_arns   = [aws_lb_target_group.app.arn]

  health_check_type         = "ELB"
  health_check_grace_period = 300

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  enabled_metrics = [
    "GroupInServiceInstances",
    "GroupDesiredCapacity",
    "GroupMinSize",
    "GroupMaxSize",
    "GroupTotalInstances",
    "GroupPendingInstances",
    "GroupTerminatingInstances",
  ]

  tag {
    key                 = "Name"
    value               = "fairbid-app"
    propagate_at_launch = true
  }

  lifecycle {
    # ASG가 런타임에 desired를 바꾸므로 Terraform이 덮어쓰지 않게
    ignore_changes = [desired_capacity]
  }
}

# =============================================================================
# Auto Scaling Policy (CPU 50% Target Tracking)
# =============================================================================
resource "aws_autoscaling_policy" "cpu_target_tracking" {
  name                   = "fairbid-cpu-target-tracking"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 50.0
  }
}
