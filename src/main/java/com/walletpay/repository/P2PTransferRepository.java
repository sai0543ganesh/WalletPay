package com.walletpay.repository;
import com.walletpay.entity.P2PTransfer; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface P2PTransferRepository extends JpaRepository<P2PTransfer,Long>{ List<P2PTransfer> findByFromUserIdOrToUserIdOrderByTimestampDesc(Long from,Long to); }
