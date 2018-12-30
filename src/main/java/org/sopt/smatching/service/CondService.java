package org.sopt.smatching.service;

import lombok.extern.slf4j.Slf4j;
import org.sopt.smatching.dto.Cond;
import org.sopt.smatching.dto.CondDetail;
import org.sopt.smatching.dto.CondSummary;
import org.sopt.smatching.dto.UserCond;
import org.sopt.smatching.model.CondRes;
import org.sopt.smatching.mapper.CondMapper;
import org.sopt.smatching.mapper.NoticeMapper;
import org.sopt.smatching.mapper.UserMapper;
import org.sopt.smatching.model.DefaultRes;
import org.sopt.smatching.utils.ResponseMessage;
import org.sopt.smatching.utils.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

@Slf4j
@Service
public class CondService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CondMapper condMapper;
    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private JwtService jwtService;

    // 맞춤조건 조회
    public DefaultRes getCondInfoByCondIdx(final int condIdx) {
        final Cond cond = condMapper.findCondByCondIdx(condIdx);
        if(cond == null)
            return DefaultRes.res(StatusCode.NO_CONTENT, ResponseMessage.NOT_EXIST_COND);

        return DefaultRes.res(StatusCode.OK, ResponseMessage.READ_COND_SUCCESS, new CondDetail(cond));
    }


    // 맞춤조건에 맞는 지원사업 개수 조회
    public DefaultRes getNoticeCountByCondDetail(CondDetail condDetail) {
        final int noticeCnt = noticeMapper.countFitNotice(new Cond(condDetail));
        return DefaultRes.res(StatusCode.OK, ResponseMessage.READ_FIT_NOTICE_CNT_SUCCESS, noticeCnt);
    }


    // 맞춤조건 추가
    @Transactional
    public DefaultRes createCond(String jwt, CondDetail condDetail) {

        // 토큰이 없으면 403 리턴
        if(jwt == null)
            return new DefaultRes(StatusCode.FORBIDDEN, ResponseMessage.NOT_EXIST_TOKEN);

        // 토큰 해독
        final JwtService.Token token = jwtService.decode(jwt);
        int userIdx = token.getUser_idx();

        // 비정상 토큰인 경우 에러 출력
        if (userIdx == -1) {
            return DefaultRes.res(StatusCode.INTERNAL_SERVER_ERROR, ResponseMessage.INVALID_TOKEN);
        }

        // 클라가 보내준 CondDetail -> DB에 저장할 Cond 변환
        Cond cond = new Cond(condDetail);
        cond.setUserIdx(userIdx);

        // Cond DB에 Insert
        try {
            condMapper.save(cond);
            return DefaultRes.res(StatusCode.CREATED, ResponseMessage.CREATED_COND, cond.getCondIdx());
        } catch(Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //Rollback
            log.error(e.getMessage());
            return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_ERROR);
        }

    }


    // 토큰에서 userIdx를 추출해서 맞춤조건 조회 - UserController 에서 사용
    public DefaultRes getCondInfoByToken(final String jwt) {

        // 토큰이 없으면 403 리턴
        if(jwt == null)
            return new DefaultRes(StatusCode.FORBIDDEN, ResponseMessage.NOT_EXIST_TOKEN);

        // 토큰 해독
        final JwtService.Token token = jwtService.decode(jwt);
        int userIdx = token.getUser_idx();

        // 비정상 토큰인 경우 500 리턴
        if(userIdx == -1)
            return new DefaultRes(StatusCode.INTERNAL_SERVER_ERROR, ResponseMessage.INVALID_TOKEN);


        // 유저의 닉네임과 설정해놓은 맞춤조건 인덱스와 이름까지만 가져옴
        List<UserCond> userCondList =  condMapper.findInfoByUserIdx(userIdx);

        // 유저가 맞춤조건을 1개도 설정하지 않은 경우 204 리턴
        if(userCondList.isEmpty())
            return DefaultRes.res(StatusCode.NO_CONTENT, ResponseMessage.NOT_FOUND_COND);

        // Response 폼에 맞게 데이터 처리
        CondRes condRes = new CondRes(userCondList.get(0).getNickName());
        for(int i=0; i<userCondList.size(); i++) {
            int condIdx = userCondList.get(i).getCondIdx();
            String condName = userCondList.get(i).getCondName();
            int noticeCnt = noticeMapper.countFitNotice(condMapper.findCondByCondIdx(userCondList.get(i).getCondIdx()));
            condRes.getCondSummaryList().add(new CondSummary(condIdx, condName, noticeCnt));
        }

        return DefaultRes.res(StatusCode.OK, ResponseMessage.READ_USERCOND_SUCCESS, condRes);
    }

}
