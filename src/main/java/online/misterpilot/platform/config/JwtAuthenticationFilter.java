package online.misterpilot.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.misterpilot.platform.entity.User;
import online.misterpilot.platform.repository.UserRepository;
import online.misterpilot.platform.util.AuthUtil;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Extracts JWT from the Authorization header, validates it,
 * loads the User, and sets the SecurityContext.
 *
 * Registered manually in SecurityConfig — NOT annotated with
 * @Component to prevent Spring Boot auto-registering it twice.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthUtil authUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            String reason = authUtil.validateToken(token);
            if (reason != null) {
                log.debug("JWT rejected on {}: {}", request.getRequestURI(), reason);
            } else {
                Long userId = authUtil.getUserIdFromJwt(token);

                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,       // principal
                                    null,       // credentials — none needed for JWT
                                    Collections.emptyList());

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Authenticated user id={} for {}", userId, request.getRequestURI());
                } else {
                    log.warn("JWT sub={} references non-existent user", userId);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
