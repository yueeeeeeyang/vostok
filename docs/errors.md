# 错误码与异常说明

本页汇总 Vostok 的错误码与异常类型，便于生产环境排障与监控告警。

## 错误码（VKErrorCode）
| Code | 枚举 | 含义 |
| --- | --- | --- |
| DK-400 | INVALID_ARGUMENT | 参数非法 |
| DK-401 | NOT_INITIALIZED | 未初始化 |
| DK-402 | CONFIG_ERROR | 配置错误 |
| DK-410 | META_ERROR | 元数据错误 |
| DK-500 | SQL_ERROR | SQL 执行错误 |
| DK-501 | SQL_TIMEOUT | SQL 超时 |
| DK-502 | SQL_CONSTRAINT | 约束违反 |
| DK-503 | SQL_SYNTAX | SQL 语法错误 |
| DK-504 | SQL_CONNECTION | SQL 连接错误 |
| DK-510 | SCAN_ERROR | 扫描错误 |
| DK-520 | POOL_ERROR | 连接池错误 |
| DK-530 | TX_ERROR | 事务错误 |
| DK-540 | CACHE_ERROR | 缓存错误 |

## File 错误码（VKFileErrorCode）
| Code | 枚举 | 含义 |
| --- | --- | --- |
| FK-400 | INVALID_ARGUMENT | 参数非法 |
| FK-401 | NOT_INITIALIZED | File 模块未初始化 |
| FK-402 | CONFIG_ERROR | File 模块配置错误 |
| FK-403 | STATE_ERROR | File 模块状态错误 |
| FK-404 | NOT_FOUND | 路径不存在 |
| FK-410 | PATH_ERROR | 路径错误（如越界） |
| FK-500 | IO_ERROR | 文件 IO 错误 |
| FK-520 | SECURITY_ERROR | 安全错误（如 Zip Slip） |
| FK-530 | UNSUPPORTED | 不支持的能力 |
| FK-540 | ZIP_BOMB_RISK | 解压风险（zip bomb 限制触发） |

## 异常类型（yueyang.vostok.data.exception）
- `VKException`：所有运行时异常的基类，包含 `VKErrorCode`
- `VKArgumentException`：参数非法（`INVALID_ARGUMENT`）
- `VKStateException`：状态异常（`NOT_INITIALIZED`）
- `VKConfigException`：配置异常（`CONFIG_ERROR`）
- `VKMetaException`：元数据异常（`META_ERROR`）
- `VKSqlException`：SQL 异常（`SQL_ERROR / SQL_TIMEOUT / SQL_CONSTRAINT / SQL_SYNTAX / SQL_CONNECTION`）
- `VKScanException`：扫描异常（`SCAN_ERROR`）
- `VKPoolException`：连接池异常（`POOL_ERROR`）
- `VKTxException`：事务异常（`TX_ERROR`）

## File 异常类型（yueyang.vostok.file.exception）
- `VKFileException`：File 模块运行时异常基类，包含 `VKFileErrorCode`

## 异常获取与统一处理
```java
try {
    Vostok.Data.findAll(User.class);
} catch (VKException e) {
    System.out.println(e.getCode() + " " + e.getMessage());
}
```

```java
try {
    Vostok.File.read("missing.txt");
} catch (yueyang.vostok.file.exception.VKFileException e) {
    System.out.println(e.getCode() + " " + e.getMessage());
}
```
