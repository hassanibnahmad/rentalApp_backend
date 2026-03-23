package com.julia_auto_cars.rental_api.controller;


import com.julia_auto_cars.rental_api.dto.ChangeEmailRequest;
import com.julia_auto_cars.rental_api.dto.ChangePasswordRequest;
import com.julia_auto_cars.rental_api.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@PreAuthorize("hasAuthority('ADMIN')")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/change-email")
    public ResponseEntity<Void> changeEmail(@RequestBody @Valid ChangeEmailRequest request) {
        accountService.changeEmail(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        accountService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

//    @PostMapping("/forgot-password")
//    @PermitAll
//    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
//        // send reset email or queue job
//        return ResponseEntity.accepted().build();
//    }
}