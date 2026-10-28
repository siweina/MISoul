# 固定发布签名

## 为什么需要固定签名

vip1–vip3 的构建没有发布密钥，构建流程退回临时调试签名，每次构建的
签名都不同，安卓因此拒绝覆盖安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），
每次升级都得先卸载旧版，模块设置也会跟着丢。

从 vip4 起，构建使用一把固定发布密钥，密钥只存在仓库 Secrets 里，
不进代码仓库。只要密钥不丢，往后的版本签名都一样，可以直接覆盖升级。

## 构建怎么用这把密钥

- 仓库 Secrets（Actions）：`MISOUL_KEYSTORE_BASE64`、`MISOUL_STORE_PASSWORD`、
  `MISOUL_KEY_PASSWORD`、`MISOUL_KEY_ALIAS`。
- 构建流程的“Restore release keystore”步骤把密钥解码成 `release.keystore`，
  并写出 `keystore.properties`（含 PKCS12 类型），`app/build.gradle` 的
  release 签名配置读它；没有 Secrets 时仍退回调试签名，方便别人直接编译。
- 密钥文件本身（PKCS12）不提交到仓库，本地临时文件在 Secrets 确认后删除。

## 升级说明

- vip3（临时签名）→ vip4（固定签名）：仍需先卸载一次，因为签名变了。
  卸载会丢模块设置，重装后需在 LSPosed 里重新启用、重新打开各开关。
- vip4 → 往后版本：直接覆盖安装即可，不用卸载；如果某次升级又报签名
  不一致，说明构建没用到固定密钥，先核对该构建是否走了固定签名流程，
  不要强行卸载重装了事。

## 密钥丢失怎么办

如果 Secrets 被删、仓库被删，固定密钥就丢了，之后只能再换一把新密钥，
而换密钥的那一版又得先卸载一次。所以不要轻易删 Secrets。
