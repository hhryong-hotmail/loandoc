const fs = require('fs');
const path = require('path');
const XLSX = require('xlsx');

// 프로그램 파일 목록과 기능 정의
const programs = [
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'server.js',
        기능: 'Express 서버, 정적 파일 제공, CORS 설정, JSON 파싱 에러 핸들링, 프록시 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.25',
        program명: 'ChangePasswordServlet.java',
        기능: '사용자 비밀번호 변경 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.26',
        program명: 'CheckSessionServlet.java',
        기능: '세션 확인 및 검증 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.01',
        program명: 'DashboardServlet.java',
        기능: '대시보드 데이터 조회 및 표시 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.02',
        program명: 'DocumentContentServlet.java',
        기능: '문서 내용 조회 및 제공 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.02',
        program명: 'DocumentsGroupsServlet.java',
        기능: '문서 그룹 관리 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.13',
        program명: 'DocumentsServlet.java',
        기능: '문서 목록 조회 및 관리 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.12',
        program명: 'EmailService.java',
        기능: '이메일 발송 서비스 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.28',
        program명: 'ForeignWorkerMasterServlet.java',
        기능: '외국인 근로자 마스터 데이터 관리 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.24',
        program명: 'ForeignWorkerServlet.java',
        기능: '외국인 근로자 정보 조회 및 관리 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.02',
        program명: 'GroupDetailsServlet.java',
        기능: '그룹 상세 정보 조회 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.01',
        program명: 'LoanDashboardServlet.java',
        기능: '대출 대시보드 데이터 조회 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'LoanEstimateServlet.java',
        기능: '대출 견적 계산 및 조회 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.01',
        program명: 'LoginServlet.java',
        기능: '사용자 로그인 인증 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.25',
        program명: 'PasswordResetServlet.java',
        기능: '비밀번호 재설정 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.12.01',
        program명: 'RegisterServlet.java',
        기능: '사용자 회원가입 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.27',
        program명: 'SessionServlet.java',
        기능: '세션 관리 기능'
    },
    {
        server: '테스트',
        개발일자: '2025.11.11',
        program명: 'UpdatePasswords.java',
        기능: '비밀번호 일괄 업데이트 유틸리티'
    },
    {
        server: '테스트',
        개발일자: '2025.11.10',
        program명: 'generate_hash.java',
        기능: '해시 생성 유틸리티'
    },
    {
        server: '테스트',
        개발일자: '2025.11.12',
        program명: 'HashPassword.java',
        기능: '비밀번호 해시 처리 유틸리티'
    },
    {
        server: '테스트',
        개발일자: '2025.11.12',
        program명: 'TestBcrypt.java',
        기능: 'Bcrypt 암호화 테스트 유틸리티'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'customer.html',
        기능: '고객 정보 조회 및 관리 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'dashboard.html',
        기능: '대시보드 메인 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'home.html',
        기능: '홈 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.11.28',
        program명: 'index.html',
        기능: '메인 인덱스 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'intro.html',
        기능: '소개 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'introCo.html',
        기능: '회사 소개 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'loanAppl.html',
        기능: '대출 신청 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.03',
        program명: 'loanDashboard.html',
        기능: '대출 대시보드 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.10',
        program명: 'login.html',
        기능: '로그인 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.01',
        program명: 'signup.html',
        기능: '회원가입 페이지'
    },
    {
        server: '테스트',
        개발일자: '2025.12.02',
        program명: 'workerInfo.html',
        기능: '근로자 정보 조회 페이지'
    }
];

// Excel 파일 생성
const ws = XLSX.utils.json_to_sheet(programs);
const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, ws, 'Programs');

// 파일 저장
const outputPath = path.join(__dirname, 'program.xlsx');
XLSX.writeFile(wb, outputPath);

console.log(`Excel 파일이 생성되었습니다: ${outputPath}`);
console.log(`총 ${programs.length}개의 프로그램이 등록되었습니다.`);

