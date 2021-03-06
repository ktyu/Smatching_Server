package org.sopt.smatching.service;

import lombok.extern.slf4j.Slf4j;
import org.sopt.smatching.model.cond.Cond;
import org.sopt.smatching.model.cond.CondDetail;
import org.sopt.smatching.model.cond.CondSummary;
import org.sopt.smatching.model.user.UserCond;
import org.sopt.smatching.model.cond.CondRes;
import org.sopt.smatching.mapper.CondMapper;
import org.sopt.smatching.mapper.NoticeMapper;
import org.sopt.smatching.model.DefaultRes;
import org.sopt.smatching.utils.ResponseMessage;
import org.sopt.smatching.utils.StatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.List;

@Slf4j
@Service
public class CondService {

    private CondMapper condMapper;
    private NoticeMapper noticeMapper;

    public CondService(CondMapper condMapper, NoticeMapper noticeMapper) {
        this.condMapper = condMapper;
        this.noticeMapper = noticeMapper;
    }

    // 맞춤조건 조회
    public DefaultRes getCondInfoByCondIdx(final int condIdx) {
        final Cond cond = condMapper.findCondByCondIdx(condIdx);
        if(cond == null)
            return DefaultRes.res(StatusCode.BAD_REQUEST, ResponseMessage.NOT_EXIST_COND);

        return DefaultRes.res(StatusCode.OK, ResponseMessage.READ_COND, new CondDetail(cond));
    }


    // 맞춤조건에 맞는 지원사업 개수 조회
    public DefaultRes getNoticeCountByCondDetail(CondDetail condDetail) {
        final int noticeCnt = noticeMapper.countFitNotice(new Cond(condDetail));
        return DefaultRes.res(StatusCode.OK, ResponseMessage.READ_FIT_NOTICE_CNT_SUCCESS, noticeCnt);
    }


    // 맞춤조건 추가
    @Transactional
    public DefaultRes createCond(final int userIdx, final CondDetail condDetail) {

        // 클라가 보내준 CondDetail -> DB에 저장할 Cond 변환
        Cond cond = new Cond(condDetail);
        cond.setUserIdx(userIdx);

        try { // 정상 추가
            int rowCnt = condMapper.save(cond); // alert는 삽입하지 않음, DB에 설정된 디폴드값(0)으로 들어가서 꺼져있는 상태
            if(rowCnt != 1)
                throw new Exception("rowCnt is NOT 1 but " + Integer.toString(rowCnt));

            // 알람이 켜져있는 맞춤조건이 있는지 확인
            int sum = condMapper.findAlertByUserIdx(userIdx); // 방금 save한 맞춤조건이 있으므로 null이 나오진 않음


            if(sum == 0) { // 알람이 켜져있는 맞춤조건이 없으면 지금 추가한 맞춤조건의 알람을 켬
                rowCnt = condMapper.updateAlert(userIdx, cond.getCondIdx(), 1); // 켜는걸로 변경
                if(rowCnt != 1)
                    throw new Exception("rowCnt is NOT 1 but " + Integer.toString(rowCnt));
            }

            return DefaultRes.res(StatusCode.CREATED, ResponseMessage.CREATED_COND, cond.getCondIdx());

        } catch(Exception e) { // DB 에러
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //Rollback
            log.error("\n- Exception Detail (below)", e);
            return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_ERROR);
        }

    }


    // 맞춤조건 변경
    @Transactional
    public DefaultRes updateCond(final int userIdx, final int condIdx, final CondDetail condDetail) {

        // 클라가 보내준 CondDetail -> DB에 저장할 Cond 변환
        Cond cond = new Cond(condDetail);

        try { // 정상 변경
            final int rowCnt = condMapper.updateByCondIdx(userIdx, condIdx, cond);

            if(rowCnt != 1)
                throw new Exception("rowCnt is NOT 1 but " + Integer.toString(rowCnt));

            return DefaultRes.res(StatusCode.NO_CONTENT, ResponseMessage.UPDATED_COND, condIdx);

        } catch(Exception e) { // DB 에러
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //Rollback
            log.error("\n- Exception Detail (below)", e);
            return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_ERROR);
        }
    }


    // 맞춤조건 삭제
    @Transactional
    public DefaultRes deleteCond(final int userIdx, final int condIdx) {

        try { // 정상 삭제
            final int rowCnt = condMapper.deleteByCondIdx(userIdx, condIdx);

            if(rowCnt != 1)
                throw new Exception("rowCnt is NOT 1 but " + Integer.toString(rowCnt));

            return DefaultRes.res(StatusCode.NO_CONTENT, ResponseMessage.DELETED_COND);

        } catch(Exception e) { // DB 에러
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //Rollback
            log.error("\n- Exception Detail (below)", e);
            return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_ERROR);
        }
    }

    // 맞춤조건 알람 ON / OFF
    @Transactional
    public DefaultRes toggleAlert(final int userIdx, final int condIdx) {
        Integer current = condMapper.findAlert(userIdx, condIdx);
        if(current == null) {
            return DefaultRes.res(StatusCode.BAD_REQUEST, ResponseMessage.NOT_EXIST_COND);
        }

        try {
            if(current == 0) { // 꺼져 있으면
                condMapper.updateAlertByUserIdx(userIdx, 0); // 일단 유저의 모든 맞춤조건들은 알람 OFF
                final int updatedCnt = condMapper.updateAlert(userIdx, condIdx, 1); // 켜는걸로 변경(현재 맞춤조건만)
                if(updatedCnt < 1)
                    return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_UPDATE_IS_ZERO);

                return DefaultRes.res(StatusCode.OK, ResponseMessage.UPDATED_COND_ALERT, true);
            }
            else { // 켜져있으면
                final int updatedCnt = condMapper.updateAlert(userIdx, condIdx, 0); // 끄는걸로 변경(현재 맞춤조건만)
                if(updatedCnt < 1)
                    return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_UPDATE_IS_ZERO);

                return DefaultRes.res(StatusCode.OK, ResponseMessage.UPDATED_COND_ALERT, false);
            }

        } catch(Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); //Rollback
            log.error("\n- Exception Detail (below)", e);
            return DefaultRes.res(StatusCode.DB_ERROR, ResponseMessage.DB_ERROR);
        }
    }


    // 유저의 맞춤조건 현황 조회 - UserController 에서 사용
    public DefaultRes getCondInfoByToken(final int userIdx) {

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

        // 맞춤조건이 2개(현재 최대 2개)가 아니면 응답코드만 206(PARTIAL_CONTENT)으로 리턴
        if(condRes.getCondSummaryList().size() != 2)
            return DefaultRes.res(StatusCode.PARTIAL_CONTENT, ResponseMessage.READ_USERCOND_SUCCESS, condRes);

        return DefaultRes.res(StatusCode.OK, ResponseMessage.READ_USERCOND_SUCCESS, condRes);
    }

}
