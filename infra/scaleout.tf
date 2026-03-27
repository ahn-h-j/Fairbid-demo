# =============================================================================
# 스케일아웃 인프라 (ALB + REST ASG + WebSocket ASG)
#
# REST/WebSocket 서버 분리:
# - ALB가 경로 기반으로 라우팅 (/ws* → WS, 나머지 → REST)
# - 각각 독립 ASG로 독립 스케일링
# - SERVER_ROLE 환경변수로 같은 이미지에서 역할 분리
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
  description = "ALB security group"
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
# Target Groups (REST + WebSocket)
# =============================================================================

# REST API Target Group
resource "aws_lb_target_group" "rest" {
  name     = "fairbid-rest-tg"
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
    Name = "fairbid-rest-tg"
  }
}

# WebSocket Target Group
resource "aws_lb_target_group" "websocket" {
  name     = "fairbid-websocket-tg"
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
    Name = "fairbid-websocket-tg"
  }
}

# =============================================================================
# ALB Listener + 경로 기반 라우팅
# =============================================================================

# 기본 리스너: REST Target Group으로 전달
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.rest.arn
  }
}

# /ws* 경로 → WebSocket Target Group으로 라우팅
resource "aws_lb_listener_rule" "websocket" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.websocket.arn
  }

  condition {
    path_pattern {
      values = ["/ws", "/ws/*"]
    }
  }
}

# =============================================================================
# Launch Templates (REST + WebSocket)
# =============================================================================

# REST API 서버용
resource "aws_launch_template" "rest" {
  name          = "fairbid-rest-lt"
  image_id      = var.app_ami_id
  instance_type = var.instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [aws_security_group.fairbid.id]

  user_data = base64encode(templatefile("${path.module}/user-data-app.sh", {
    infra_private_ip = aws_instance.fairbid.private_ip
    server_role      = "api"
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "fairbid-rest"
    }
  }

  iam_instance_profile {
    name = "fairbid-app-profile"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# WebSocket 서버용
resource "aws_launch_template" "websocket" {
  name          = "fairbid-websocket-lt"
  image_id      = var.app_ami_id
  instance_type = var.instance_type
  key_name      = var.key_name

  vpc_security_group_ids = [aws_security_group.fairbid.id]

  user_data = base64encode(templatefile("${path.module}/user-data-app.sh", {
    infra_private_ip = aws_instance.fairbid.private_ip
    server_role      = "ws"
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name = "fairbid-websocket"
    }
  }

  iam_instance_profile {
    name = "fairbid-app-profile"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# =============================================================================
# Auto Scaling Groups (REST + WebSocket)
# =============================================================================

# REST API ASG
resource "aws_autoscaling_group" "rest" {
  name                = "fairbid-rest-asg"
  min_size            = var.asg_min_size
  max_size            = var.asg_max_size
  desired_capacity    = var.asg_desired_capacity
  vpc_zone_identifier = data.aws_subnets.default.ids
  target_group_arns   = [aws_lb_target_group.rest.arn]

  health_check_type         = "ELB"
  health_check_grace_period = 300

  launch_template {
    id      = aws_launch_template.rest.id
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
    value               = "fairbid-rest"
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity]
  }
}

# WebSocket ASG
resource "aws_autoscaling_group" "websocket" {
  name                = "fairbid-websocket-asg"
  min_size            = var.ws_asg_min_size
  max_size            = var.ws_asg_max_size
  desired_capacity    = var.ws_asg_desired_capacity
  vpc_zone_identifier = data.aws_subnets.default.ids
  target_group_arns   = [aws_lb_target_group.websocket.arn]

  health_check_type         = "ELB"
  health_check_grace_period = 300

  launch_template {
    id      = aws_launch_template.websocket.id
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
    value               = "fairbid-websocket"
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity]
  }
}

# =============================================================================
# Auto Scaling Policies (REST만 - WS는 커넥션 수 기반으로 별도 구성 예정)
# =============================================================================

# REST: ALB 요청 수 기반 Target Tracking
resource "aws_autoscaling_policy" "request_count_target_tracking" {
  name                      = "fairbid-rest-request-target-tracking"
  autoscaling_group_name    = aws_autoscaling_group.rest.name
  policy_type               = "TargetTrackingScaling"
  estimated_instance_warmup = 120

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ALBRequestCountPerTarget"
      resource_label         = "${aws_lb.app.arn_suffix}/${aws_lb_target_group.rest.arn_suffix}"
    }
    target_value = 5000
  }
}

# REST: CPU Step Scaling (비상 안전망)
resource "aws_cloudwatch_metric_alarm" "cpu_high" {
  alarm_name          = "fairbid-rest-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "REST CPU 80% 초과 시 스케일아웃 (비상 안전망)"

  dimensions = {
    AutoScalingGroupName = aws_autoscaling_group.rest.name
  }

  alarm_actions = [aws_autoscaling_policy.cpu_step_scaling.arn]
}

resource "aws_autoscaling_policy" "cpu_step_scaling" {
  name                   = "fairbid-rest-cpu-step-scaling"
  autoscaling_group_name = aws_autoscaling_group.rest.name
  policy_type            = "StepScaling"
  adjustment_type        = "ChangeInCapacity"

  step_adjustment {
    scaling_adjustment          = 1
    metric_interval_lower_bound = 0
  }
}
