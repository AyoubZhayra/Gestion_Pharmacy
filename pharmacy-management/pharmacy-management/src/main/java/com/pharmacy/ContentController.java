package com.pharmacy;

import java.util.Optional;
import java.util.List;
import java.io.File;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Date;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import com.pharmacy.model.MyUser;
import com.pharmacy.model.MyUserRepository;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.MedicineRepository;
import com.pharmacy.model.Sale;
import com.pharmacy.model.SaleItem;
import com.pharmacy.repository.SaleRepository;
import com.pharmacy.repository.SaleItemRepository;

@Controller
public class ContentController {

  @Autowired
  private MyUserRepository userRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private MedicineRepository medicineRepository;

  @Autowired
  private SaleRepository saleRepository;

  @Autowired
  private SaleItemRepository saleItemRepository;

  @Value("${upload.path}")
  private String uploadPath;

  @GetMapping("/home")
  public String handleWelcome() {
    return "home";
  }

  @GetMapping("/admin/dashboard")
  public String handleAdminHome(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Get statistics
    long totalUsers = userRepository.count();
    long totalMedicines = medicineRepository.count();
    long totalSales = saleRepository.count();
    
    // Calculate total revenue
    double totalRevenue = saleRepository.findAll().stream()
            .mapToDouble(Sale::getTotal)
            .sum();
            
    // Get low stock medicines (less than 10 items)
    List<Medicine> lowStockMedicines = medicineRepository.findAll().stream()
            .filter(m -> m.getQuantity() < 10)
            .collect(Collectors.toList());
            
    // Get recent sales (last 5)
    List<Sale> recentSales = saleRepository.findAll().stream()
            .sorted((s1, s2) -> s2.getDate().compareTo(s1.getDate()))
            .limit(5)
            .collect(Collectors.toList());
    
    model.addAttribute("username", username);
    model.addAttribute("totalUsers", totalUsers);
    model.addAttribute("totalMedicines", totalMedicines);
    model.addAttribute("totalSales", totalSales);
    model.addAttribute("totalRevenue", totalRevenue);
    model.addAttribute("lowStockMedicines", lowStockMedicines);
    model.addAttribute("recentSales", recentSales);
    
    return "dashboard_admin";
  }

  @GetMapping("/user/dashboard")
  public String handleUserHome(Model model) {
    // Get current user
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Fetch all medicines
    List<Medicine> medicines = medicineRepository.findAll();
    
    model.addAttribute("username", username);
    model.addAttribute("medicines", medicines);
    return "dashboard_user";
  }

  @GetMapping("/login")
  public String handleLogin() {
    return "custom_login";
  }

  @GetMapping("/update-profile")
  public String updateProfile(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    model.addAttribute("username", username);
    return "update_profile";
  }

  @PostMapping("/update-profile")
  public String updateUserProfile(@RequestParam String username,
                                  @RequestParam(required = false) String currentPassword,
                                  @RequestParam(required = false) String newPassword,
                                  RedirectAttributes redirectAttributes) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    
    if (userOptional.isPresent()) {
        MyUser user = userOptional.get();
        
        // Update username if changed
        if (!username.equals(user.getUsername()) && !username.isEmpty()) {
            Optional<MyUser> existingUser = userRepository.findByUsername(username);
            if (existingUser.isEmpty()) {
                user.setUsername(username);
            } else {
                redirectAttributes.addFlashAttribute("error", "Username already exists");
                return "redirect:/update-profile";
            }
        }
        
        // Update password if provided
        if (currentPassword != null && !currentPassword.isEmpty() 
            && newPassword != null && !newPassword.isEmpty()) {
            if (passwordEncoder.matches(currentPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);
                redirectAttributes.addFlashAttribute("success", "Password updated successfully. Please login again.");
                return "redirect:/login";
            } else {
                redirectAttributes.addFlashAttribute("error", "Current password is incorrect");
                return "redirect:/update-profile";
            }
        }
        
        // Save changes if username was updated
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "Profile updated successfully");
        
        // Update the authentication with new username and existing authorities
        Authentication newAuth = new UsernamePasswordAuthenticationToken(
            user.getUsername(), 
            user.getPassword(),
            auth.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
    } else {
        redirectAttributes.addFlashAttribute("error", "User not found");
    }
    
    return "redirect:/update-profile";
  }

  @GetMapping("/create-user")
  public String showCreateUserForm(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    model.addAttribute("username", username);
    return "create_user";
  }

  @PostMapping("/create-user")
  public String createUser(@RequestParam String username,
                          @RequestParam String password,
                          RedirectAttributes redirectAttributes) {
      // Check if username already exists
      if (userRepository.findByUsername(username).isPresent()) {
          redirectAttributes.addFlashAttribute("error", "Username already exists");
          return "redirect:/create-user";
      }

      // Create new user
      MyUser newUser = new MyUser();
      newUser.setUsername(username);
      newUser.setPassword(passwordEncoder.encode(password));
      newUser.setRole("USER");  // Set default role to USER

      userRepository.save(newUser);
      redirectAttributes.addFlashAttribute("success", "User created successfully");
      return "redirect:/create-user";
  }

  @PostMapping("/register-admin")
  public String registerAdmin(@RequestParam String username,
                          @RequestParam String password,
                          RedirectAttributes redirectAttributes) {
      // Check if username already exists
      if (userRepository.findByUsername(username).isPresent()) {
          redirectAttributes.addFlashAttribute("error", "Username already exists");
          return "redirect:/create-user";
      }

      // Create new admin user
      MyUser newUser = new MyUser();
      newUser.setUsername(username);
      newUser.setPassword(passwordEncoder.encode(password));
      newUser.setRole("ADMIN");  // Set role to ADMIN for this form

      userRepository.save(newUser);
      redirectAttributes.addFlashAttribute("success", "Admin user created successfully");
      return "redirect:/create-user";
  }

  @GetMapping("/table-users")
  public String showUsersTable(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Fetch all users from database
    List<MyUser> users = userRepository.findAll();
    
    model.addAttribute("username", username);
    Long currentUserId = userOptional.map(MyUser::getId).orElse(0L);
    model.addAttribute("currentUserId", currentUserId);
    model.addAttribute("users", users);
    return "table_users";
  }

  @PostMapping("/delete-user")
  public String deleteUser(@RequestParam Long userId, RedirectAttributes redirectAttributes) {
    // Get current user
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> currentUser = userRepository.findByUsername(auth.getName());
    
    // Find user to delete
    Optional<MyUser> userToDelete = userRepository.findById(userId);
    
    if (userToDelete.isPresent() && currentUser.isPresent()) {
        MyUser user = userToDelete.get();
        
        // Prevent deletion of admin with ID = 1
        if (userId == 1L) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete main admin account");
            return "redirect:/table-users";
        }
        
        // Only main admin can delete other admins
        if (user.getRole().equals("ADMIN") && currentUser.get().getId() != 1L) {
            redirectAttributes.addFlashAttribute("error", "Only the main admin can delete admin accounts");
            return "redirect:/table-users";
        }
        
        try {
            userRepository.deleteById(userId);
            redirectAttributes.addFlashAttribute("success", "User deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting user");
        }
    }
    return "redirect:/table-users";
  }

  @GetMapping("/edit-user/{id}")
  public String showEditUserForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
    // Get current user for navbar
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Get user to edit
    Optional<MyUser> editUserOptional = userRepository.findById(id);
    if (editUserOptional.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", "User not found");
        return "redirect:/table-users";
    }
    
    model.addAttribute("username", username);
    model.addAttribute("editUser", editUserOptional.get());
    return "edit_user";
  }

  @PostMapping("/edit-user")
  public String editUser(@RequestParam Long userId,
                        @RequestParam String username,
                        @RequestParam(required = false) String newPassword,
                        @RequestParam String role,
                        RedirectAttributes redirectAttributes) {
    // Get current user
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> currentUser = userRepository.findByUsername(auth.getName());
                          
    Optional<MyUser> userOptional = userRepository.findById(userId);
    if (userOptional.isPresent() && currentUser.isPresent()) {
        MyUser user = userOptional.get();
        
        // Only main admin can edit other admins
        if (user.getRole().equals("ADMIN") && currentUser.get().getId() != 1L) {
            redirectAttributes.addFlashAttribute("error", "Only the main admin can modify admin accounts");
            return "redirect:/table-users";
        }

        // Check if new username is already taken by another user
        if (!username.equals(user.getUsername())) {
            Optional<MyUser> existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                redirectAttributes.addFlashAttribute("error", "Username already exists");
                return "redirect:/edit-user/" + userId;
            }
            user.setUsername(username);
        }

        // Update password if provided
        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        // Update role
        user.setRole(role);

        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "User updated successfully");
        return "redirect:/table-users";
    }
    return "redirect:/table-users";
  }

  @GetMapping("/add-medicine")
  public String showAddMedicineForm(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    model.addAttribute("username", username);
    return "add_medicine";
  }

  @PostMapping("/add-medicine")
  public String addMedicine(@RequestParam String name,
                          @RequestParam String description,
                          @RequestParam Double price,
                          @RequestParam Integer quantity,
                          @RequestParam MultipartFile image,
                          RedirectAttributes redirectAttributes) {
      try {
          // Create directory with absolute path
          File uploadDir = new File(uploadPath);
          if (!uploadDir.exists()) {
              if (!uploadDir.mkdirs()) {
                  throw new RuntimeException("Could not create upload directory: " + uploadPath);
              }
          }

          // Generate unique filename
          String uuidFile = UUID.randomUUID().toString();
          String resultFilename = uuidFile + "." + "png";

          // Save file
          File destFile = new File(uploadDir, resultFilename);
          image.transferTo(destFile);

          // Create and save medicine
          Medicine medicine = new Medicine();
          medicine.setName(name);
          medicine.setDescription(description);
          medicine.setPrice(price);
          medicine.setQuantity(quantity);
          medicine.setImageUrl(resultFilename);

          medicineRepository.save(medicine);
          
          redirectAttributes.addFlashAttribute("success", "Medicine added successfully");
      } catch (Exception e) {
          redirectAttributes.addFlashAttribute("error", "Error adding medicine: Could not save image. Please ensure the uploads directory exists and has proper permissions.");
          e.printStackTrace(); // Log the full error
      }
      
      return "redirect:/add-medicine";
  }

  @GetMapping("/list-medicines")
  public String showMedicinesList(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Fetch all medicines
    List<Medicine> medicines = medicineRepository.findAll();
    
    model.addAttribute("username", username);
    model.addAttribute("medicines", medicines);
    return "list_medicines";
  }

  @PostMapping("/delete-medicine")
  public String deleteMedicine(@RequestParam Long medicineId, RedirectAttributes redirectAttributes) {
    try {
        Optional<Medicine> medicineOptional = medicineRepository.findById(medicineId);
        
        if (medicineOptional.isPresent()) {
            Medicine medicine = medicineOptional.get();
            
            // Delete the image file
            if (medicine.getImageUrl() != null) {
                File imageFile = new File(uploadPath + "/" + medicine.getImageUrl());
                if (imageFile.exists()) {
                    imageFile.delete();
                }
            }
            
            // Delete the medicine from database
            medicineRepository.deleteById(medicineId);
            redirectAttributes.addFlashAttribute("success", "Medicine deleted successfully");
        } else {
            redirectAttributes.addFlashAttribute("error", "Medicine not found");
        }
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error", "Error deleting medicine: " + e.getMessage());
    }
    
    return "redirect:/list-medicines";
  }

  @GetMapping("/edit-medicine/{id}")
  public String showEditMedicineForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
    // Get current user for navbar
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Get medicine to edit
    Optional<Medicine> medicineOptional = medicineRepository.findById(id);
    if (medicineOptional.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", "Medicine not found");
        return "redirect:/list-medicines";
    }
    
    model.addAttribute("username", username);
    model.addAttribute("medicine", medicineOptional.get());
    return "edit_medicine";
  }

  @PostMapping("/edit-medicine")
  public String editMedicine(@RequestParam Long medicineId,
                         @RequestParam String name,
                         @RequestParam String description,
                         @RequestParam Double price,
                         @RequestParam Integer quantity,
                         @RequestParam(required = false) MultipartFile image,
                         RedirectAttributes redirectAttributes) {
    try {
        Optional<Medicine> medicineOptional = medicineRepository.findById(medicineId);
        
        if (medicineOptional.isPresent()) {
            Medicine medicine = medicineOptional.get();
            
            // Update basic information
            medicine.setName(name);
            medicine.setDescription(description);
            medicine.setPrice(price);
            medicine.setQuantity(quantity);
            
            // Update image if provided
            if (image != null && !image.isEmpty()) {
                // Delete old image
                if (medicine.getImageUrl() != null) {
                    File oldImage = new File(uploadPath + "/" + medicine.getImageUrl());
                    if (oldImage.exists()) {
                        oldImage.delete();
                    }
                }
                
                // Save new image
                String uuidFile = UUID.randomUUID().toString();
                String resultFilename = uuidFile + "." + "png";
                
                File destFile = new File(uploadPath + "/" + resultFilename);
                image.transferTo(destFile);
                
                medicine.setImageUrl(resultFilename);
            }
            
            medicineRepository.save(medicine);
            redirectAttributes.addFlashAttribute("success", "Medicine updated successfully");
        } else {
            redirectAttributes.addFlashAttribute("error", "Medicine not found");
        }
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error", "Error updating medicine: " + e.getMessage());
    }
    
    return "redirect:/list-medicines";
  }

  @PostMapping("/api/checkout")
  @ResponseBody
  public ResponseEntity<?> processCheckout(@RequestBody List<Map<String, Object>> cartItems, 
                                           @AuthenticationPrincipal UserDetails userDetails) {
      try {
          // Create main sale record
          Sale sale = new Sale();
          sale.setUsername(userDetails.getUsername());
          sale.setDate(new Date());
          sale.setTotal(cartItems.stream()
              .mapToDouble(item -> Double.parseDouble(item.get("price").toString()) * 
                         Integer.parseInt(item.get("quantity").toString()))
              .sum());
          Sale savedSale = saleRepository.save(sale);

          // Save individual sale items
          for (Map<String, Object> item : cartItems) {
              SaleItem saleItem = new SaleItem();
              saleItem.setSale(savedSale);
              saleItem.setMedicineId(Long.parseLong(item.get("id").toString()));
              saleItem.setQuantity(Integer.parseInt(item.get("quantity").toString()));
              saleItem.setPrice(Double.parseDouble(item.get("price").toString()));
              saleItemRepository.save(saleItem);

              // Update medicine quantity
              Medicine medicine = medicineRepository.findById(saleItem.getMedicineId())
                  .orElseThrow(() -> new RuntimeException("Medicine not found"));
              medicine.setQuantity(medicine.getQuantity() - saleItem.getQuantity());
              medicineRepository.save(medicine);
          }

          return ResponseEntity.ok(Map.of("message", "Checkout successful", "saleId", savedSale.getId()));
      } catch (Exception e) {
          return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
      }
  }

  @GetMapping("/total-sales")
  public String showTotalSales(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Optional<MyUser> userOptional = userRepository.findByUsername(auth.getName());
    String username = userOptional.map(MyUser::getUsername).orElse(auth.getName());
    
    // Get all sales
    List<Sale> allSales = saleRepository.findAll();
    
    // Calculate total revenue
    double totalRevenue = allSales.stream()
            .mapToDouble(Sale::getTotal)
            .sum();
    
    // Get recent sales (last 5)
    List<Sale> recentSales = allSales.stream()
            .sorted((s1, s2) -> s2.getDate().compareTo(s1.getDate()))
            .limit(5)
            .collect(Collectors.toList());
    
    // Get total number of sales
    long totalSalesCount = allSales.size();
    
    // Calculate average sale value
    double averageSaleValue = totalSalesCount > 0 ? totalRevenue / totalSalesCount : 0;
    
    model.addAttribute("username", username);
    model.addAttribute("totalRevenue", totalRevenue);
    model.addAttribute("totalSalesCount", totalSalesCount);
    model.addAttribute("averageSaleValue", averageSaleValue);
    model.addAttribute("recentSales", recentSales);
    
    return "total_sales";
  }

}