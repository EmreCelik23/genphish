package com.genphish.campaign.entity.enums;

public enum TargetingType {
    ALL_COMPANY,    // Send to all employees in the company
    DEPARTMENT,     // Send to a specific department
    INDIVIDUAL,     // Send to hand-picked employees
    HIGH_RISK       // Send to employees with high risk scores
}
