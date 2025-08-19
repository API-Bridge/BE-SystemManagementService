-- System Management Service 초기 데이터베이스 스키마
-- PostgreSQL 15+

-- 데이터베이스 초기화
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 외부 API 정보 테이블
CREATE TABLE IF NOT EXISTS external_apis (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    endpoint VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL DEFAULT 'GET',
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    retry_count INTEGER NOT NULL DEFAULT 3,
    circuit_breaker_enabled BOOLEAN NOT NULL DEFAULT true,
    circuit_breaker_threshold INTEGER NOT NULL DEFAULT 5,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 헬스체크 결과 테이블
CREATE TABLE IF NOT EXISTS health_check_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_api_id UUID NOT NULL REFERENCES external_apis(id),
    status VARCHAR(20) NOT NULL,
    response_time_ms INTEGER,
    error_message TEXT,
    checked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- API 호출 로그 테이블
CREATE TABLE IF NOT EXISTS api_call_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    external_api_id UUID NOT NULL REFERENCES external_apis(id),
    request_url VARCHAR(1000) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    response_status INTEGER,
    response_time_ms INTEGER,
    success BOOLEAN NOT NULL,
    error_message TEXT,
    called_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 에러 로그 테이블
CREATE TABLE IF NOT EXISTS error_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_name VARCHAR(255) NOT NULL,
    error_type VARCHAR(100) NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    severity VARCHAR(20) NOT NULL DEFAULT 'ERROR',
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_health_check_results_api_id ON health_check_results(external_api_id);
CREATE INDEX IF NOT EXISTS idx_health_check_results_checked_at ON health_check_results(checked_at);
CREATE INDEX IF NOT EXISTS idx_api_call_logs_api_id ON api_call_logs(external_api_id);
CREATE INDEX IF NOT EXISTS idx_api_call_logs_called_at ON api_call_logs(called_at);
CREATE INDEX IF NOT EXISTS idx_error_logs_service_name ON error_logs(service_name);
CREATE INDEX IF NOT EXISTS idx_error_logs_occurred_at ON error_logs(occurred_at);

-- 트리거 함수: updated_at 자동 업데이트
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 트리거 적용
CREATE TRIGGER update_external_apis_updated_at 
    BEFORE UPDATE ON external_apis 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 샘플 데이터 삽입
INSERT INTO external_apis (name, base_url, endpoint, method, timeout_seconds, retry_count) VALUES
('User Service', 'http://user-service:8080', '/api/v1/users/health', 'GET', 10, 3),
('Payment Service', 'http://payment-service:8080', '/api/v1/payments/health', 'GET', 15, 3),
('Notification Service', 'http://notification-service:8080', '/api/v1/notifications/health', 'GET', 5, 2),
('Inventory Service', 'http://inventory-service:8080', '/api/v1/inventory/health', 'GET', 20, 3)
ON CONFLICT DO NOTHING;

-- 파티셔닝을 위한 함수 (선택사항 - 대용량 데이터 처리시)
-- CREATE OR REPLACE FUNCTION create_monthly_partition(table_name text, start_date date)
-- RETURNS void AS $$
-- DECLARE
--     partition_name text;
--     end_date date;
-- BEGIN
--     partition_name := table_name || '_' || to_char(start_date, 'YYYY_MM');
--     end_date := start_date + interval '1 month';
--     
--     EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF %I 
--                     FOR VALUES FROM (%L) TO (%L)', 
--                    partition_name, table_name, start_date, end_date);
-- END;
-- $$ LANGUAGE plpgsql;