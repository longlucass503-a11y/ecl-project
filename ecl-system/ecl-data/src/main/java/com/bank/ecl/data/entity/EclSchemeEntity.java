package com.bank.ecl.data.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("tbl_ecl_scheme")
public class EclSchemeEntity {

    @TableId
    private String schemeId;

    private String schemeCode;

    private String schemeName;

    private String schemeVersion;

    private String status;

    private LocalDate effectiveDate;

    private LocalDateTime effectiveAt;

    private LocalDateTime expiredAt;

    private BigDecimal discountRate;

    private BigDecimal defaultCcf;

    private BigDecimal defaultLgd;

    private BigDecimal lgdFloor;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private String description;
}
