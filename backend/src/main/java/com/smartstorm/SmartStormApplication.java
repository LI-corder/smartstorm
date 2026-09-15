package com.smartstorm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartStorm 智能头脑风暴室 - 后端启动入口
 */
@SpringBootApplication









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
