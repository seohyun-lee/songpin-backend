package sws.songpin.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import sws.songpin.global.exception.CustomException;
import sws.songpin.global.exception.ErrorCode;
import sws.songpin.global.exception.ErrorDto;

import java.io.IOException;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Log4j2
public class JwtFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if(authorizationHeader != null && authorizationHeader.startsWith("Bearer")){
            String token = authorizationHeader.substring(7);

            try{
                if(jwtUtil.validateToken(token)){
                    Authentication authentication = jwtUtil.getAuthentication(token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RedisConnectionFailureException e){
                SecurityContextHolder.clearContext();
                request.setAttribute("exception",ErrorCode.EXTERNAL_API_ERROR);
            } catch (CustomException e){
                request.setAttribute("exception",e.getErrorCode());
            }
        }
        filterChain.doFilter(request,response);
    }
}
