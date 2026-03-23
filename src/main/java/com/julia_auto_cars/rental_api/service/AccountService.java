package com.julia_auto_cars.rental_api.service;

import com.julia_auto_cars.rental_api.dto.ChangeEmailRequest;
import com.julia_auto_cars.rental_api.dto.ChangePasswordRequest;

// Service interface for account-related operations, such as changing email and password. it defines the contract for the AccountService implementation, ensuring that any class implementing this interface will provide the necessary functionality for managing user accounts. This promotes a clean separation of concerns and allows for easier testing and maintenance of the account management logic.
public interface AccountService {
    void changeEmail(ChangeEmailRequest request);
    void changePassword(ChangePasswordRequest request);
}
