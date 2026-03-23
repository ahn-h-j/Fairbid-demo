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
# Auto Scaling Policy (메인: ALB 요청 수 기반 Target Tracking)
# - 인스턴스당 1분간 요청 10,000개 초과 시 확장
# - VU 100 → 분당 ~12,000 → 2대 / VU 200 → ~24,000 → 3대 / VU 300 → ~36,000 → 4대
# =============================================================================
resource "aws_autoscaling_policy" "request_count_target_tracking" {
  name                      = "fairbid-request-target-tracking"
  autoscaling_group_name    = aws_autoscaling_group.app.name
  policy_type               = "TargetTrackingScaling"
  estimated_instance_warmup = 120 # 인스턴스 부팅 + 앱 시작 대기 (2분)

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.app.arn_suffix}/${aws_lb_target_group.app.arn_suffix}"
    }
    target_value = 5000
  }
}

# =============================================================================
# Auto Scaling Policy (보조: CPU Step Scaling - 비상 안전망)
# - CPU 80% 이상 시 +1대 추가 (Target Tracking과 충돌 방지를 위해 Step Scaling 사용)
# - 평소에는 RequestCount 정책이 주도, CPU 비정상 급등 시에만 발동
# =============================================================================
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "fairbid-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "CPU 80% 초과 시 스케일아웃 (비상 안전망)"

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.app.name
  }

  alarm_actions = [aws_autoscaling_policy.cpu_step_scaling.arn]
}

resource "aws_autoscaling_policy" "cpu_step_scaling" {
  name                   = "fairbid-cpu-step-scaling"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "StepScaling"
  adjustment_type        = "ChangeInCapacity"

  step_adjustment {
    scaling_adjustment          = 1  # +1대만 추가
    metric_interval_lower_bound = 0
  }
}
