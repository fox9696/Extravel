package com.ict.extravel.domain.pointexchange.service;

import com.ict.extravel.domain.member.entity.Member;
import com.ict.extravel.domain.member.repository.MemberRepository;
import com.ict.extravel.domain.pointexchange.MakePayRequest;
import com.ict.extravel.domain.pointexchange.dto.PayInfoDto;
import com.ict.extravel.domain.pointexchange.dto.response.PayConfirmResponseDTO;
import com.ict.extravel.domain.pointexchange.dto.response.PaymentDto;
import com.ict.extravel.domain.pointexchange.dto.request.PayRequest;
import com.ict.extravel.domain.pointexchange.dto.response.PayReadyResDto;
import com.ict.extravel.domain.pointexchange.entity.PointCharge;
import com.ict.extravel.domain.pointexchange.entity.Wallet;
import com.ict.extravel.domain.pointexchange.repository.PointChargeRepository;
import com.ict.extravel.domain.pointexchange.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class KakaoPayService {
    private final MakePayRequest makePayRequest;
    private final MemberRepository memberRepository;
    private final PointChargeRepository pointChargeRepository;
    private final WalletRepository walletRepository;

    @Value("${pay.admin-key}")
    private String adminKey;


    /** 카오페이 결제를 시작하기 위해 상세 정보를 카카오페이 서버에 전달하고 결제 고유 번호(TID)를 받는 단계입니다.
     * 어드민 키를 헤더에 담아 파라미터 값들과 함께 POST로 요청합니다.
     * 테스트  가맹점 코드로 'TC0ONETIME'를 사용 */
    @Transactional
    public PayReadyResDto getRedirectUrl(PayInfoDto payInfoDto)throws Exception{
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String name = authentication.getName();

        Member member = memberRepository.findById(payInfoDto.getId()).orElseThrow(() -> new Exception("해당 유저가 존재하지 않습니다."));

        HttpHeaders headers=new HttpHeaders();

        /** 요청 헤더 */
        String auth = "KakaoAK " + adminKey;
        headers.set("Content-type","application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization",auth);

        /** 요청 Body */
        PayRequest payRequest=makePayRequest.getReadyRequest(payInfoDto);

        /** Header와 Body 합쳐서 RestTemplate로 보내기 위한 밑작업 */
        HttpEntity<MultiValueMap<String, String>> urlRequest = new HttpEntity<>(payRequest.getMap(), headers);

        /** RestTemplate로 Response 받아와서 DTO로 변환후 return */
        RestTemplate rt = new RestTemplate();
        PayReadyResDto payReadyResDto = rt.postForObject(payRequest.getUrl(), urlRequest, PayReadyResDto.class);

        log.info("payReadyResDto 응답옴! {}", payReadyResDto);

        // String으로 받은 create_at을 LocalDateTime으로 변환
        String createdAtStr = payReadyResDto.getCreated_at();  // "2023-06-21T15:30:00" 같은 형식의 문자열
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr, formatter);


        // 여기서 tbl_charge_history에 데이터 추가
        PointCharge pointCharge = PointCharge.builder()
                .tid(payReadyResDto.getTid())
                .member(member)
                .amount(Integer.parseInt(payInfoDto.getPrice()))
                .plusPoint(payInfoDto.getPlusPoint())
                .createdAt(createdAt)
                .approvedAt(null)
                .status(PointCharge.Status.PENDING)
                .inUse(true)
                .build();
        System.out.println("pointCharge = " + pointCharge);

        PointCharge save = pointChargeRepository.save(pointCharge);
        System.out.println(save);
        log.info("DB에 1차 저장 완료!");

        return payReadyResDto;
    }

    @Transactional
    public PaymentDto getApprove(String pgToken, Integer id)throws Exception{

        String tid = pointChargeRepository.findCurrentTidbyId(id);
        log.info("service 안 getApprove 메서드 안에 있음. tid를 테이블에서 검색해 옴! {}", tid);

        HttpHeaders headers=new HttpHeaders();
        String auth = "KakaoAK " + adminKey;

        /** 요청 헤더 */
        headers.set("Content-type","application/x-www-form-urlencoded;charset=utf-8");
        headers.set("Authorization",auth);

        /** 요청 Body */
        PayRequest payRequest=makePayRequest.getApproveRequest(tid, id, pgToken);

        log.info("kakaoPayService에서 승인 요청을 위한 body {}", payRequest.toString());

        /** Header와 Body 합쳐서 RestTemplate로 보내기 위한 밑작업 */
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(payRequest.getMap(), headers);

        log.info("kakaoPayService에서  승인 요청을 보내기 직전입니다");

        // 요청 보내기
        RestTemplate rt = new RestTemplate();
        PaymentDto paymentDto = rt.postForObject(payRequest.getUrl(), requestEntity, PaymentDto.class);

        // DB에 승인 결과 저장
        log.info("승인 요청의 결과 {}", paymentDto);
        pointChargeRepository.updatePointChargeBy(paymentDto.getTid(), paymentDto.getApprovedAt(), PointCharge.Status.SUCCESS, false);

        return paymentDto;
    }

    public PayConfirmResponseDTO findTidInfo(String tid) {
        log.info("tid로 결제 정보 가져오는 service");
        PointCharge pointCharge = pointChargeRepository.findById(tid).orElseThrow();
        log.info("pointChargeRepo에서 pointCharge SELECT 완료! {}", pointCharge);

        PayConfirmResponseDTO dto = PayConfirmResponseDTO.toDto(pointCharge);
        return dto;
    }

    public void calcTotalResult(Integer id, PaymentDto paymentDto) {
        // 멤버 등급에 따라 plus point 산정해 총 적립 포인트 계산하는 메서드
        float newEtPoint = calcTotalPoint(id, paymentDto.getAmount().getTotal());
        log.info("산정된 et point: {}", newEtPoint);
        BigDecimal newEtiPointBD  = BigDecimal.valueOf(newEtPoint);

        // 현재 가지고 있는 et point 조회
        BigDecimal currentEtPointBD = BigDecimal.valueOf(0);
        Optional<Wallet> wallet = walletRepository.findById(id);
        if (wallet.isPresent()) {
            currentEtPointBD = wallet.get().getEtPoint();
        }

        // DB Wallet에 보유 포인트 업데이트
        Wallet wallet1 = updateWallet(id, currentEtPointBD.add(newEtiPointBD));
        log.info("wallet 저장 결과: {}", wallet1.toString());
    }

    private float calcTotalPoint(Integer id, int amount) {
        Member member = memberRepository.findById(id).orElseThrow();
        float plusRate = 0;
        log.info("멤버의 등급을 표기합니다 {}", member.getGrade());
        switch (member.getGrade()) {
            case BRONZE:
                plusRate = 0.001F;
                break;
            case SILVER:
                plusRate = 0.002F;
                break;
            case GOLD:
                plusRate = 0.003F;
                break;
        }
        // 소수점 이하 3자리로 반올림
        return Math.round(amount * (1 + plusRate) * 1000) / 1000f;
    }

    public Wallet updateWallet(Integer memberId, BigDecimal etPoint) {
        Wallet wallet = walletRepository.findById(memberId).orElseThrow();
        log.info("upsertWallet 안에서 wallet을 찾은 결과 {}", Objects.requireNonNull(wallet));

        wallet.setEtPoint(etPoint);
        log.info("wallet 저장합니다 {}", wallet);

        return walletRepository.save(wallet);
    }
}
