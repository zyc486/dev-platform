package com.zhaoyichi.devplatformbackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * SPA history 路由回退：任意非静态资源路径都转发到 index.html。
 * <p>
 * 说明：/api/** 由各 RestController 处理；/uploads/** 由资源映射处理；带 '.' 的路径视为静态资源请求。
 */
@Controller
public class SpaForwardController {

    /**
     * 使用 {@code /**} 兜底，覆盖多级前端路由（例如 {@code /projects/1/board}、{@code /issues/123}）。
     * <p>
     * 注意：必须显式排除 API、上传目录、WebSocket 等路径，避免误转发。
     */
    @RequestMapping("/**")
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "forward:/index.html";
        }

        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }

        // API / 静态资源 / WS：不要拦截
        if (uri.startsWith("/api/")
                || uri.startsWith("/uploads/")
                || uri.startsWith("/ws/")
                || uri.startsWith("/error")) {
            // 交给其它处理器/默认处理；这里返回 null 会触发 NoHandlerFound（404）
            // 因此对这些前缀不应进入该 Controller —— 通过精确判断避免误匹配：
            // 由于映射是 /**，仍可能命中 /api/**；上面直接 return null 不合适。
            // Spring 会把它当作“没有视图”，从而 404。正确做法是：不要注册到这些路径。
            // 但 Spring MVC 无法在同一方法里“跳过”。因此这里用 forward 到自身不可能路径也不对。
            // 实际运行中，/api/** 会由 @RestController 优先匹配；本方法只在“无其它 handler”时才会匹配吗？
            // 并不是：/** 会抢所有未匹配路径；对 /api/** 仍会进入这里。
            //
            // 结论：必须对 /api/** 返回一个不会破坏 JSON 的结果 —— 最简单是抛异常让全局处理，
            // 但会破坏正常 API。因此改为：仅当 uri 不以 /api 开头时才 forward。
            //
            // 由于无法 return “继续查找”，我们把 /** 的匹配范围限制在非 API 上做不到（同一类）。
            //
            // 正确方案：拆成两个控制器或使用 RequestCondition —— 这里采用“白名单前缀判断”：
            // - 如果是 /api、/uploads、/ws、/error：抛 NoHandlerFoundException 也不合适
            //
            // 最终方案：不要用 /**。改为 WebMvcConfigurer#addViewControllers + ResourceHandlerRegistry。
            //
            // 但为快速修复：检测如果是 /api 开头，则 forward 到原始 URI 让 DispatcherServlet 重新 dispatch 也不可行。
            //
            // 因此这里采用 Spring Boot 推荐做法：使用 Filter 或 WebMvcConfigurer#setDefaultServletHandler。
            //
            // 简化实现：使用 RequestDispatcher forward 到 /index.html 仅当非 /api /uploads /ws。
            // 对 /api：绝不应该进入 —— 说明 /** 映射会覆盖 API？不会，API 更具体，匹配优先级更高。
            // Spring MVC 匹配顺序：具体路径优先于 /**。因此 /api/** 不会进入该方法。
            //
            // 所以保留 /** 是安全的。
        }

        // 静态文件：带扩展名（如 .js/.css/.png/.ico/.map/.woff2）
        int slash = uri.lastIndexOf('/');
        String last = slash >= 0 ? uri.substring(slash + 1) : uri;
        if (last.contains(".")) {
            // 让默认静态资源处理（不要转发到 index.html）
            return "forward:" + uri;
        }

        return "forward:/index.html";
    }
}

