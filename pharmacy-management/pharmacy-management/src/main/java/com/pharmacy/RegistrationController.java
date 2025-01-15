package com.pharmacy;

import com.pharmacy.model.MyUser;
import com.pharmacy.model.MyUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.ModelAttribute;


@Controller
public class RegistrationController {

    @Autowired
    private MyUserRepository myUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

  @PostMapping("/register/user")
    public String createUser(@ModelAttribute MyUser user, RedirectAttributes redirectAttributes) {
        try {
            user.setRole("USER");
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            myUserRepository.save(user);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful! You can now log in.");
            return "redirect:/register";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Registration failed. Please try again.");
            return "redirect:/register";
        }
    }

    @GetMapping("/register")
    public String handleRegisterUser() {
        return "register";
      }
}
