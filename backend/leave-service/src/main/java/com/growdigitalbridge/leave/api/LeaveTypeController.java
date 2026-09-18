package com.growdigitalbridge.leave.api;

import com.growdigitalbridge.leave.api.dto.LeaveTypeDtos;
import com.growdigitalbridge.leave.service.LeaveTypeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leave/types")
class LeaveTypeController {

    private final LeaveTypeService service;

    LeaveTypeController(LeaveTypeService service) {
        this.service = service;
    }

    @GetMapping
    List<LeaveTypeDtos.Response> list() {
        return service.listActive();
    }
}
