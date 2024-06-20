package com.ict.extravel.domain.pointexchange.service;

import com.ict.extravel.domain.member.entity.Member;
import com.ict.extravel.domain.member.repository.MemberRepository;
import com.ict.extravel.domain.pointexchange.MakePayRequest;
import com.ict.extravel.domain.pointexchange.dto.PayInfoDto;
import com.ict.extravel.domain.pointexchange.dto.response.PaymentDto;
import com.ict.extravel.domain.pointexchange.dto.request.PayRequest;
import com.ict.extravel.domain.pointexchange.dto.response.PayReadyResDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RequiredArgsConstructor
@Service
public class KakaoPayService {
    private final MakePayRequest makePayRequest;
    private final MemberRepository memberRepository;

    @Value("${pay.admin-key}")
    private String adminKey;


    /** 카오페이 결제를 시작하기 위해 상세 정보를 카카오페이 서버에 전달하고 결제 고유 번호(TID)를 받는 단계입니다.
     * 어드민 키를 헤더에 담아 파라미터 값들과 함께 POST로 요청합니다.
     * 테스트  가맹점 코드로 'TC0ONETIME'를 사용 */
    @Transactional
    public PayReadyResDto getRedirectUrl(PayInfoDto payInfoDto)throws Exception{
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String name = authentication.getName();


        Member member=memberRepository.findByEmail("testemail12@test.com")
                .orElseThrow(()-> new Exception("해당 유저가 존재하지 않습니다."));

        Integer id = member.getId();


        HttpHeaders headers=new HttpHeaders();

        /** 요청 헤더 */
        String auth = "KakaoAK " + adminKey;
        headers.set("Content-type","application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization",auth);

        /** 요청 Body */
        PayRequest payRequest=makePayRequest.getReadyRequest(id,payInfoDto);

        /** Header와 Body 합쳐서 RestTemplate로 보내기 위한 밑작업 */
        HttpEntity<MultiValueMap<String, String>> urlRequest = new HttpEntity<>(payRequest.getMap(), headers);

        /** RestTemplate로 Response 받아와서 DTO로 변환후 return */
        RestTemplate rt = new RestTemplate();
        PayReadyResDto payReadyResDto = rt.postForObject(payRequest.getUrl(), urlRequest, PayReadyResDto.class);

        member.updateTid(payReadyResDto.getTid());
        log.info("member tid update 완료! {}", payReadyResDto.toString());

        return payReadyResDto;
    }

    @Transactional
    public PaymentDto getApprove(String pgToken, Integer id)throws Exception{


        Member member=memberRepository.findById(id)
                .orElseThrow(()->new Exception("해당 유저가 존재하지 않습니다."));

        String tid=member.getTid();


        HttpHeaders headers=new HttpHeaders();
        String auth = "KakaoAK " + adminKey;

        /** 요청 헤더 */
        headers.set("Content-type","application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization",auth);

        /** 요청 Body */
        PayRequest payRequest=makePayRequest.getApproveRequest(tid,id,pgToken);

        log.info("kakaoPayService에서 승인 요청을 위한 body {}", payRequest.toString());

        /** Header와 Body 합쳐서 RestTemplate로 보내기 위한 밑작업 */
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(payRequest.getMap(), headers);

        log.info("kakaoPayService에서  승인 요청을 보내기 직전입니다");

        // 요청 보내기
        RestTemplate rt = new RestTemplate();
        PaymentDto paymentDto = rt.postForObject(payRequest.getUrl(), requestEntity, PaymentDto.class);

        log.info("승인 요청의 결과 {}", paymentDto);
        return paymentDto;
    }


}
