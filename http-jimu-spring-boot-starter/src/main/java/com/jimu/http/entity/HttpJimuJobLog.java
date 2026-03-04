package com.jimu.http.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@TableName("http_jimu_job_log")
public class HttpJimuJobLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configId;
    private String httpId;
    private String inputParams;
    private String outputResult;
    private String status; // SUCCESS, ERROR
    private String errorMsg;
    private Long duration;
    private LocalDateTime createTime;
}
