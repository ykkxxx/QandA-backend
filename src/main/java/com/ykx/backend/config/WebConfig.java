import com.ykx.backend.intercepter.AdminTokenInterceptor;
import com.ykx.backend.intercepter.AuthInterceptor;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Resource
    private AdminTokenInterceptor adminTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 管理员拦截器只拦截 admin 相关接口
        registry.addInterceptor(adminTokenInterceptor)
                .addPathPatterns("/api/admin/**", "/api/blacklist/**");

        // 用户认证拦截器拦截其他接口，放行 admin 相关接口
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/admin/**", "/api/blacklist/**", "/api/login", "/api/register");
    }
}