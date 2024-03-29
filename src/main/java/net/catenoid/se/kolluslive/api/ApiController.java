package net.catenoid.se.kolluslive.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.catenoid.se.kolluslive.config.KollusConfig;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private KollusConfig kollusConfig;

    @Autowired
    private Auth auth;

    @Autowired
    private Channel channel;

    /***
     * API 연동을 위한 토큰 확인을 위한 API
     * 설정 값중 token (Personal Token)이 지정 되어 있으면 우선 출력하며
     * TODO: OAUTH2 를 이용한 토큰 획득은 추후 개발
     */

    @RequestMapping("/token")
    public String getToken() throws Exception{
        return auth.getToken();
    }

    /*
    * 채널 정보 수집 API
    * 설정파일 기반으로 API 연동 정보를 수집해 라이브 API 호출
    *  본 API는 다양한 정보가 수집되나
    * 재생을 위한 정보만 필터링하여 출력
    *
    * 채널 타이틀, 채널키, 방송여부, 전용플레이어 사용 여부, 스냅샷
    *
     */
    @RequestMapping("/channel")
    public String getChannel() throws Exception{

        ObjectMapper mapper = new ObjectMapper();


        List<Map<String, Object>> channels = (List<Map<String, Object>>)channel.getChannels(null, null, null, null, null, null).get("data");
        List<Map<String, Object>> summary = new ArrayList<Map<String, Object>>();
        for(Map<String, Object> channel : channels){
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("ch_title", channel.get("title").toString());
            item.put("ch_key", channel.get("key").toString());
            Map<String, Object> broadcast = (Map<String, Object>)channel.get("latest_broadcast");
            if(!broadcast.isEmpty()){
                item.put("is_onair", Boolean.parseBoolean(broadcast.get("is_onair").toString()));
            }else{
                item.put("is_onair", false);
            }
            Map<String, Object> player = (Map<String, Object>)channel.get("media_player_policy");
            boolean exculisve = "force_exclusive_player".equals(player.get("type").toString()) && "1".equals(player.get("value").toString());
            item.put("player", exculisve);
            if(channel.get("idle_screen_url").toString() != null) {
                item.put("snapshot", channel.get("idle_screen_url").toString());
            }
            else{
                item.put("snapshot", "https://i.ytimg.com/vi/6kCSVT3r_Qg/hqdefault.jpg");
            }

            summary.add(item);
        }

        return mapper.writeValueAsString(summary);
    }

    @RequestMapping("/jwt")
    public String getPlayUrl(@RequestParam(value = "cuid", required = false) String cuid, // 사용자 아이디
                             @RequestParam(value = "lmckey", required = true) String lmckey, //라이브 채널키
                             @RequestParam(value = "lmcpf", required = false) String lmcpf, // 화질 선택, 빈값일경우 자동 설정(ABR)
                             @RequestParam(value="title", required = false) String title, // 방송 제목
                             @RequestParam(value="seek", required = false) boolean seek //플레이어 SEEK 가능 여부 설정
    ) throws Exception{


        //JWT Payload 생성
        Map<String, Object> payload = new HashMap<String, Object>();
        if(cuid != null && !cuid.isEmpty()) payload.put("cuid", cuid);
        if(lmckey.isEmpty()) throw new Exception("올바른 채널키를 넣어주세요");
        payload.put("lmckey", lmckey);
        if(lmcpf != null && !lmcpf.isEmpty()) payload.put("lmcpf", lmcpf);
        if(title != null && !title.isEmpty()) payload.put("title", title);
        if(seek) payload.put("seek", seek);

        //URL 만료 시간 설정 설정 파일에 지정하고자 하는 분단위를 입력하면 URL 생성 시간이후 지정 시간까지만 URL 사용 가능
        Date now = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(now);
        c.add(Calendar.MINUTE, kollusConfig.getExpt());
        long expt = c.getTime().getTime();
        payload.put("expt", expt);
        ObjectMapper mapper = new ObjectMapper();

        //JWT 토큰 생성
        Map<String, String> header = new HashMap<String, String>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        String headerJson = mapper.writeValueAsString(header);
        String payloadJson = mapper.writeValueAsString(payload);

        String encodedheader = Base64.encodeBase64URLSafeString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedpayload = Base64.encodeBase64URLSafeString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String content = String.format("%s.%s", encodedheader, encodedpayload);
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(kollusConfig.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signatureBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.encodeBase64URLSafeString(signatureBytes);
        String jwtToken = String.format("%s.%s", content, signature);

        Map<String, String> response = new HashMap<String, String>();
        String playUrl = String.format("https://v-live-kr.kollus.com/s?jwt=%s&custom_key=%s", jwtToken, kollusConfig.getCustomKey());
        response.put("url", playUrl);
        return mapper.writeValueAsString(response);


    }




}
