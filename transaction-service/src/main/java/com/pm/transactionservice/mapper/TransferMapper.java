package com.pm.transactionservice.mapper;

import com.pm.transactionservice.dto.TransferResponseDto;
import com.pm.transactionservice.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransferMapper {
    TransferResponseDto toDto(Transaction transaction);
}
