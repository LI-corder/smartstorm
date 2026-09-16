package com.smartstorm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartStorm 智能头脑风暴室 - 后端启动入口
 *
 * <p>{@code @EnableScheduling} 用于第四阶段的存证锚定定时任务
 * （见 {@code com.smartstorm.task.ChainAnchorTask}）。</p>
 */
@SpringBootApplication
@EnableScheduling









@MapperScan("com.smartstorm.mapper")
public class SmartStormApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartStormApplication.class, args);
        System.out.println("""

                ███████╗███████╗██╗███████╗████████╗ ██████╗ ██████╗ ███╗   ███╗
                ██╔════╝██╔════╝██║██╔════╝╚══██╔══╝██╔═══██╗██╔══██╗████╗ ████║
                ███████╗█████╗  ██║███████╗   ██║   ██║   ██║██████╔╝██╔████╔██║
                ╚════██║██╔══╝  ██║╚════██║   ██║   ██║   ██║██╔══██╗██║╚██╔╝██║
                ███████║███████╗██║███████║   ██║   ╚██████╔╝██║  ██║██║ ╚═╝ ██║
                ╚══════╝╚══════╝╚═╝╚══════╝   ╚═╝    ╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝
                后台已启动: http://localhost:8080
                """);      
    }
}
