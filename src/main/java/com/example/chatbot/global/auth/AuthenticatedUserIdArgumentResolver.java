package com.example.chatbot.global.auth;

import com.example.chatbot.global.error.AppException;
import com.example.chatbot.global.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthenticatedUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object attr = webRequest.getAttribute(ApiKeyAuthFilter.AUTHENTICATED_USER_ID_ATTR, NativeWebRequest.SCOPE_REQUEST);
        if (attr instanceof Long userId) {
            return userId;
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
