package com.example.service;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.function.Supplier;

@SpringBootApplication
public class ServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceApplication.class, args);
    }

    @Bean
    ApplicationRunner applicationRunner(CustomerService service) {
        return args -> service.all().forEach(System.out::println);
    }
}

@RestController
class CustomerHttpController {
    private final CustomerService service;
    private final ObservationRegistry registry;

    CustomerHttpController(CustomerService service, ObservationRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @GetMapping("/customers/{name}")
    Collection<Customer> byName(@PathVariable String name) {
        Assert.state(Character.isUpperCase(name.charAt(0)), "the name must start with a capital letter !");
        return Observation
                .createNotStarted("by-name", this.registry)
                .observe(() -> service.byName(name));
    }

    @GetMapping("/customers")
    Collection<Customer> all() {
        return this.service.all();
    }
}

@ControllerAdvice
class ErrorHandlingControllerAdvice {
    @ExceptionHandler
    public ProblemDetail handler(IllegalStateException e, HttpServletRequest request) {

        request.getHeaderNames().asIterator().forEachRemaining(name -> System.out.println(name+"="+request.getHeader(name)));
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST.value());
        pd.setDetail(e.getLocalizedMessage());
        return pd;
    }
}

@Service
class CustomerService {
    private final JdbcTemplate template;
    private final RowMapper<Customer> customerRowMapper = (rs, rowNum) -> new Customer(rs.getInt("id"), rs.getString("name"));

    CustomerService(JdbcTemplate template) {
        this.template = template;
    }

    Collection<Customer> all() {
        return this.template.query("select * from customer", this.customerRowMapper);
    }

    Collection<Customer> byName(String name) {
        return this.template.query("select * from customer where name =?", this.customerRowMapper, name);
    }
}

//Look ma, no Lombok!
record Customer(Integer id, String name) {
}