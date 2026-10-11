# 启动器图标：已改为「原创矢量自适应图标」，本脚本已废弃。
#
# 现行方案（全部是文本资源，可随源码公开）：
#   app/src/main/res/drawable/ic_launcher_foreground.xml   # 前景矢量：聊天气泡 + 勾选
#   app/src/main/res/values/colors.xml                     # ic_launcher_background 背景色
#   app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml     # 组装 background/foreground/monochrome
#   app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
#
# 本脚本原先用于把一张**位图插画**切成各密度 PNG 图层，并改写上面的 adaptive-icon XML。
# 现在图标是纯矢量：再执行本脚本会用位图覆盖矢量定义的 XML，因此**请勿执行**。
# 若确实需要位图图标，请另行编写脚本，并确保不引入任何第三方素材/商标/角色/字体。

Write-Output "本脚本已废弃：启动器图标现为矢量自适应图标，见 app/src/main/res/drawable/ic_launcher_foreground.xml"
