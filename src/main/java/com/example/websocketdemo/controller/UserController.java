package com.example.websocketdemo.controller;

import com.example.websocketdemo.entity.UserInfo;
import com.example.websocketdemo.model.RefreshToken;
import com.example.websocketdemo.model.ResponseToken;
import com.example.websocketdemo.model.User;
import com.example.websocketdemo.model.UserSaveRequest;
import com.example.websocketdemo.provider.JwtTokenProvider;
import com.example.websocketdemo.repository.RefreshTokenRepository;
import com.example.websocketdemo.service.RefreshTokenService;
import com.example.websocketdemo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Date;

@Slf4j
@RequestMapping("api/v1/users")
@RequiredArgsConstructor
@Controller
public class UserController {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signup")
    @ResponseBody
    public ResponseEntity signup(User user) {
        if (userService.isExistEmail(user.getEmail())) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        userService.save(user);
        return new ResponseEntity(HttpStatus.OK);
    }


    //로그인 성공 후 로그인 페이지 접근 막기
    @GetMapping("/login")
    public String showLoginForm(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        RefreshToken findRefreshToken = refreshTokenService.findByToken(refreshToken);
        if (findRefreshToken != null && refreshTokenService.verifyExpiration(findRefreshToken)) {
            return "redirect:/chatRoomList";
        }
        return "login";
    }


    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity login(HttpServletResponse response, @RequestBody UserSaveRequest userSaveRequest) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserInfo userInfo = userService.loadUserByUsername(userSaveRequest.getEmail());

        if (encoder.matches(userSaveRequest.getPassword(), userInfo.getPassword())) {

            //accessToken
            String accessToken = jwtTokenProvider.createToken(userInfo.getEmail(), userInfo.getAuthorities());

            //accessToken Expiration
            Date expiration = jwtTokenProvider.getTokenExpiration(accessToken);

            //refreshToken
            String refreshToken = refreshTokenService.createRefreshToken(RefreshToken.create(userInfo.getEmail()));


            Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
            // optional properties
            refreshTokenCookie.setSecure(true);
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setPath("/");


            response.addCookie(refreshTokenCookie);

            ResponseToken responseToken = new ResponseToken(accessToken, expiration);
            return new ResponseEntity<>(responseToken, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/logout")
    public String logout(@CookieValue(name = "refreshToken", required = false) String refreshToken) {

        RefreshToken findRefreshToken = refreshTokenService.findByToken(refreshToken);
        if (findRefreshToken != null) {
            refreshTokenRepository.deleteByToken(refreshToken);
        }
        return "redirect:/login";
    }


    @PostMapping("/refreshToken")
    @ResponseBody
    public ResponseEntity refreshToken(@CookieValue(name = "refreshToken", required = false) String refreshToken, Principal principal) {

        RefreshToken token = refreshTokenService.findByToken(refreshToken);

        if (token != null && refreshTokenService.verifyExpiration(token)) {
            UserInfo userInfo = userService.loadUserByUsername(token.getEmail());
            String accessToken = jwtTokenProvider.createToken(userInfo.getEmail(), userInfo.getAuthorities());
            Date expiration = jwtTokenProvider.getTokenExpiration(accessToken);

            ResponseToken responseToken = new ResponseToken(accessToken, expiration);
            return new ResponseEntity<>(responseToken, HttpStatus.OK);
        }

        return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
    }


    //테스트용 데이터 추가
    @PostConstruct
    public void init() {
        userService.save(new User("test@naver.com", "zns9dyek951956"));
        userService.save(new User("test2@naver.com", "zns9dyek951956"));
    }
}
