package com.nitditto.repository;

import com.nitditto.model.User;
import com.nitditto.payload.dto.UserDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  User findByEmail(String email);
}
