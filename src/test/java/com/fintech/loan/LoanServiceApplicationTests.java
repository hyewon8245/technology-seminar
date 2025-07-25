package com.fintech.loan;

import com.fintech.loan.domain.entity.Loan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class LoanServiceApplicationTests {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void redis연결_테스트() {
        // given
        String key = "test:loan";
        Loan loan = new Loan();
        loan.setId(1L);
        loan.setProductName("테스트 대출 상품");

        // when
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        ops.set(key, loan);

        // then
        Object result = ops.get(key);
        assertThat(result).isNotNull();
        assertThat(((Loan) result).getProductName()).isEqualTo("테스트 대출 상품");

        System.out.println("✅ Redis 연동 성공 : " + result);
    }
}