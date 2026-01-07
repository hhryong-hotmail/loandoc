# 테스트 모드에서 KB저축은행=웰컴저축은행 동일값 처리 소스 코드

## 상황 설명
- 테스트 모드일 때
- KB저축은행=웰컴저축은행의 예상금리와 예상대출금액이 16.0%, 3000만원
- 통신속도(comm)가 둘 다 1
- 대출 조회 결과 예상대출금이 KB저축은행=웰컴저축은행으로 동일하게 나옴

## 1. 클라이언트 사이드: 테스트 모드 확인 및 예상한도 재계산

### 파일: server/src/main/webapp/loanAppl.html

```1336:1399:server/src/main/webapp/loanAppl.html
            // testMode 확인 (env-select-result를 우선 확인)
            let isTestMode = false;
            const envSelectResult = document.getElementById('env-select-result');
            if (envSelectResult && envSelectResult.value !== 'setting') {
                const envValue = envSelectResult.value;
                isTestMode = (envValue === 'test' || envValue === 'Test' || envValue === 'TEST');
            } else {
                // fallback: 상단의 env-select 확인
                const envSelect = document.getElementById('env-select');
                if (envSelect) {
                    const envValue = envSelect.value;
                    isTestMode = (envValue === 'test' || envValue === 'Test' || envValue === 'TEST');
                }
            }
            
            // 예가람저축은행 예상금리 강제 설정 (운영 모드일 때만)
            if (!isTestMode && result && Array.isArray(result.banks)) {
                result.banks.forEach(function(bank) {
                    if (bank && bank.bankName && String(bank.bankName).trim() === '예가람저축은행') {
                        bank.estimatedRate = 16.5;
                    }
                });
            }
            
            // 테스트 모드일 때 예상한도 재계산
            if (isTestMode && result && Array.isArray(result.banks) && inputData) {
                const annualIncome = parseFloat(inputData.annualIncome) || 0;
                const remainMonths = parseInt(inputData.remainMonths) || 0;
                
                result.banks.forEach(function(bank) {
                    if (!bank || !bank.bankName) return;
                    
                    // test_bank_info에서 해당 은행 정보 찾기
                    const bankInfo = test_bank_info.find(b => 
                        b && b.bank_name && String(b.bank_name).trim() === String(bank.bankName).trim()
                    );
                    
                    if (bankInfo && bankInfo.weight && bankInfo.max_limit) {
                        // 예상한도 = 연소득 * 잔여체류개월수 * 가중치 / 10
                        // test_bank_info의 weight는 35.00, 36.00 등이므로 100으로 나눠서 0.35, 0.36로 변환
                        const weightFactor = bankInfo.weight / 100.0;
                        let calculatedLimit = (annualIncome * remainMonths * weightFactor) / 10;
                        
                        // max_limit보다 크면 max_limit으로 대치
                        if (calculatedLimit > bankInfo.max_limit) {
                            calculatedLimit = bankInfo.max_limit;
                        }
                        
                        // 소수점 이하 반올림
                        bank.estimatedLimit = Math.round(calculatedLimit);
                        
                        console.log('[Test Mode] 재계산된 예상한도:', {
                            bankName: bank.bankName,
                            annualIncome: annualIncome,
                            remainMonths: remainMonths,
                            weight: bankInfo.weight,
                            weightFactor: weightFactor,
                            calculatedLimit: calculatedLimit,
                            maxLimit: bankInfo.max_limit,
                            finalLimit: bank.estimatedLimit
                        });
                    }
                });
            }
```

## 2. 클라이언트 사이드: 정렬 로직 (금리 기준 정렬 시)

### 파일: server/src/main/webapp/loanAppl.html

```1487:1585:server/src/main/webapp/loanAppl.html
            // Populate result rows
            if(result && Array.isArray(result.banks)){
                // Sort banks: '가능' first, '불가' last
                // Within '가능' banks, sort by estimatedRate (if sortByRate) or estimatedLimit (if sortByLimit)
                // 인덱스를 추가하여 안정적인 정렬을 위해
                const banksWithIndex = result.banks.map((bank, idx) => ({ bank, originalIndex: idx }));
                
                const sortedBanks = banksWithIndex.sort((itemA, itemB) => {
                    const a = itemA.bank;
                    const b = itemB.bank;
                    
                    // comm 값이 null이거나 undefined이거나 0보다 작으면 통신장애로 처리
                    const commValueA = (a.comm !== null && a.comm !== undefined) ? a.comm : null;
                    const hasCommFailureA = (commValueA === null || commValueA === undefined || commValueA <= 0);
                    const commValueB = (b.comm !== null && b.comm !== undefined) ? b.comm : null;
                    const hasCommFailureB = (commValueB === null || commValueB === undefined || commValueB <= 0);
                    
                    const hasFailureA = 
                        (a.visaType && !a.visaType.valid) ||
                        (a.country && !a.country.valid) ||
                        (a.age && !a.age.valid) ||
                        (a.annualIncome && !a.annualIncome.valid) ||
                        (a.employmentDate && !a.employmentDate.valid) ||
                        (a.visaExpiry && !a.visaExpiry.valid) ||
                        (a.healthInsurance && !a.healthInsurance.valid) ||
                        hasCommFailureA; // comm 값이 null이거나 0 이하면 대출불가
                    
                    const hasFailureB = 
                        (b.visaType && !b.visaType.valid) ||
                        (b.country && !b.country.valid) ||
                        (b.age && !b.age.valid) ||
                        (b.annualIncome && !b.annualIncome.valid) ||
                        (b.employmentDate && !b.employmentDate.valid) ||
                        (b.visaExpiry && !b.visaExpiry.valid) ||
                        (b.healthInsurance && !b.healthInsurance.valid) ||
                        hasCommFailureB; // comm 값이 null이거나 0 이하면 대출불가
                    
                    // '가능' (no failure) comes before '불가' (has failure)
                    if (!hasFailureA && hasFailureB) return -1;
                    if (hasFailureA && !hasFailureB) return 1;
                    
                    // If both are '가능', sort by selected option
                    if (!hasFailureA && !hasFailureB) {
                        if (sortByRate) {
                            // Sort by rate (lower rate first)
                            const rateA = a.estimatedRate || 999;
                            const rateB = b.estimatedRate || 999;
                            if (rateA !== rateB) {
                                return rateA - rateB;
                            }
                            // 금리가 같으면 예상한도가 큰 순서대로
                            const limitA = a.estimatedLimit || 0;
                            const limitB = b.estimatedLimit || 0;
                            if (limitA !== limitB) {
                                return limitB - limitA;
                            }
                            // 금리와 한도가 모두 같으면 comm 값이 작은 순서대로
                            // 단, 음수(-) 값은 장애를 의미하므로 가장 뒤로 배치
                            let commA = (a.comm !== null && a.comm !== undefined) ? a.comm : 999999;
                            let commB = (b.comm !== null && b.comm !== undefined) ? b.comm : 999999;
                            // 음수는 장애로 처리하여 뒤로 배치
                            if (commA < 0) commA = 999999;
                            if (commB < 0) commB = 999999;
                            if (commA !== commB) {
                                return commA - commB;
                            }
                            // 모든 값이 같으면 원래 인덱스로 정렬
                            return itemA.originalIndex - itemB.originalIndex;
                        } else {
                            // Sort by limit (higher limit first)
                            const limitA = a.estimatedLimit || 0;
                            const limitB = b.estimatedLimit || 0;
                            if (limitA !== limitB) {
                                return limitB - limitA;
                            }
                            // 한도가 같으면 예상금리가 작은 순서대로
                            const rateA = a.estimatedRate || 999;
                            const rateB = b.estimatedRate || 999;
                            if (rateA !== rateB) {
                                return rateA - rateB;
                            }
                            // 금리와 한도가 모두 같으면 comm 값이 작은 순서대로
                            // 단, 음수(-) 값은 장애를 의미하므로 가장 뒤로 배치
                            let commA = (a.comm !== null && a.comm !== undefined) ? a.comm : 999999;
                            let commB = (b.comm !== null && b.comm !== undefined) ? b.comm : 999999;
                            // 음수는 장애로 처리하여 뒤로 배치
                            if (commA < 0) commA = 999999;
                            if (commB < 0) commB = 999999;
                            if (commA !== commB) {
                                return commA - commB;
                            }
                            // 모든 값이 같으면 원래 인덱스로 정렬
                            return itemA.originalIndex - itemB.originalIndex;
                        }
                    }
                    
                    // 둘 다 불가능한 경우 원래 인덱스로 정렬
                    return itemA.originalIndex - itemB.originalIndex;
                }).map(item => item.bank); // bank 객체만 추출
```

## 3. 서버 사이드: 정렬 로직

### 파일: server/src/main/java/com/loandoc/LoanEstimateServlet.java

```99:139:server/src/main/java/com/loandoc/LoanEstimateServlet.java
            // Sort by rank, then by comm (communication speed)
            // 금리와 대출금액의 순서대로 체크 후, 통신속도가 작은 것부터 1순위에 가깝게 정렬
            // 단, 음수(-) 값은 장애를 의미하므로 가장 뒤로 배치
            List<ObjectNode> sortedResults = new ArrayList<>();
            results.forEach(node -> sortedResults.add((ObjectNode) node));
            sortedResults.sort((a, b) -> {
                // 1순위: rank 비교 (금리와 대출금액이 반영된 순위)
                int rankA = a.has("rank") && !a.get("rank").isNull() ? a.get("rank").asInt() : 999;
                int rankB = b.has("rank") && !b.get("rank").isNull() ? b.get("rank").asInt() : 999;
                int rankCompare = Integer.compare(rankA, rankB);
                if (rankCompare != 0) {
                    return rankCompare;
                }
                
                // 2순위: comm 값 비교 (통신속도가 작은 것부터 1순위에 가깝게)
                // 음수(-) 값은 장애를 의미하므로 가장 큰 값으로 처리하여 뒤로 배치
                int commA = 999999; // 기본값 (장애 또는 null)
                int commB = 999999; // 기본값 (장애 또는 null)
                
                if (a.has("comm") && !a.get("comm").isNull()) {
                    commA = a.get("comm").asInt();
                    if (commA < 0) {
                        commA = 999999; // 음수는 장애로 처리하여 뒤로 배치
                    }
                }
                
                if (b.has("comm") && !b.get("comm").isNull()) {
                    commB = b.get("comm").asInt();
                    if (commB < 0) {
                        commB = 999999; // 음수는 장애로 처리하여 뒤로 배치
                    }
                }
                
                int commCompare = Integer.compare(commA, commB);
                if (commCompare != 0) {
                    return commCompare; // 작은 값이 앞으로 (빠른 통신이 우선)
                }
                
                // 3순위: 모든 값이 같으면 원래 순서 유지
                return 0;
            });
```

## 4. 서버 사이드: 예상한도 계산 (테스트 모드)

### 파일: server/src/main/java/com/loandoc/LoanEstimateServlet.java

```686:739:server/src/main/java/com/loandoc/LoanEstimateServlet.java
        // 예상한도 계산
        double calculatedLimit;
        double maxLimitValue;
        
        if (testMode) {
            // testMode일 때: 연소득 × 잔여체류개월수 × 가중치 (test_bank_info의 weight를 100으로 나눈 값) / 10
            // weightFactor는 이미 weight / 100.0으로 저장되어 있음
            calculatedLimit = (annualIncome * remainMonths * bank.weightFactor) / 10.0;
            // test_bank_info의 max_limit은 만원 단위이므로 그대로 사용
            maxLimitValue = bank.maxLimit;
            
            // 디버그 로그 추가
            logger.log(Level.INFO, String.format("[TEST MODE] 예상한도 계산 - 은행: %s, 연소득: %.0f, 잔여체류: %d, weightFactor: %.4f, calculatedLimit: %.2f, maxLimit: %.0f", 
                bank.name, annualIncome, remainMonths, bank.weightFactor, calculatedLimit, maxLimitValue));
        } else {
            // 운영 모드: 기존 로직 유지
            calculatedLimit = (annualIncome * remainMonths * bank.weightFactor) / 10.0;
            maxLimitValue = bank.maxLimit;
        }
        
        // 단, 최고한도를 초과할 수 없음
        double finalLimit = Math.min(calculatedLimit, maxLimitValue);
        
        // 디버그 로그 추가
        if (testMode) {
            logger.log(Level.INFO, String.format("[TEST MODE] 최종 예상한도 - 은행: %s, calculatedLimit: %.2f, maxLimit: %.0f, finalLimit: %.0f", 
                bank.name, calculatedLimit, maxLimitValue, finalLimit));
        }
        
        // 소수점 이하 반올림
        finalLimit = Math.round(finalLimit);
        
        result.put("estimatedLimit", finalLimit);
        
        if (bank.estimatedRate != null) {
            // 소수점 2자리까지 반올림
            double roundedRate = Math.round(bank.estimatedRate * 100.0) / 100.0;
            result.put("estimatedRate", roundedRate);
        } else {
            result.putNull("estimatedRate");
        }

        if (bank.rank != null) {
            result.put("rank", bank.rank);
        } else {
            result.putNull("rank");
        }

        // comm 값 추가
        if (bank.comm != null) {
            result.put("comm", bank.comm);
        } else {
            result.putNull("comm");
        }
```

## 핵심 로직 설명

### 동일값이 나오는 이유

1. **예상금리**: test_bank_info 테이블에서 KB저축은행과 웰컴저축은행 모두 `current_rate = 16.0`으로 설정되어 있음
2. **예상대출금액**: 
   - 계산식: `(연소득 × 잔여체류개월수 × 가중치) / 10`
   - 최고한도 제한: `max_limit = 3000만원`
   - 두 은행 모두 가중치와 최고한도가 동일한 경우, 계산 결과가 같으면 둘 다 3000만원으로 제한됨
3. **통신속도**: 둘 다 `comm = 1`로 동일
4. **정렬 로직**: 
   - 금리 기준 정렬 시: 금리(16.0) → 한도(3000) → 통신속도(1) 순으로 비교
   - 모든 값이 동일하면 `originalIndex`로 정렬하지만, 이 경우에도 동일한 값으로 표시됨

### 정렬 우선순위 (금리 기준 정렬 시)

1. **1순위**: 예상금리 (낮은 순)
2. **2순위**: 예상한도 (높은 순) - 금리가 같을 때
3. **3순위**: 통신속도(comm) (작은 순) - 금리와 한도가 같을 때
4. **4순위**: 원래 인덱스 - 모든 값이 같을 때

### 테스트 모드에서의 처리

- 클라이언트 사이드에서 `test_bank_info`를 사용하여 예상한도를 재계산
- 서버 사이드에서도 `test_bank_info` 테이블의 데이터를 사용하여 예상한도 계산
- 두 은행의 가중치, 최고한도, 금리가 동일하면 동일한 예상대출금액이 나올 수 있음
