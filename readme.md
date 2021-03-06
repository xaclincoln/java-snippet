
# 客户端和服务器的交互过程

1. 客户端连接服务器，向其发送XML配置文件，帧中包括了Md5的校验码
2. 服务器接收配置文件，根据校验码验证是否有误码，如果验证不通过，则告诉客户端重传，如果验证通过，则告诉客户端断点续传的位置
3. 客户端接收配置文件验证信息，如果正确则从指定的位置读取目标文件，一帧帧向服务器发送数据文件，如果一个块发送完成，会向服务器发送一个块校验请求帧；如果配置文件验证失败，则重新发送配置文件。如果只要验证失败，则客户端重发
4. 当服务器验证配置文件通过时，开始接收数据帧，如果接收到了客户端的块验证请求帧时，则根据配置文件中的块Md5校验码校验是否有误码，根据校验结果，回复客户端是否校验成功
5. 客户端在发送块校验请求后，等待服务器的回复，如果校验失败，则重发此块，如果成功，则发后续的文件块，直至文件结果



# 服务器实现断点续传的过程
在服务器接收到一个配置文件后，根据配置文件中的文件名到接收目录下查找指定名称的文件是否存在，如果不存在，则告诉客户端从头开始发送。如果找到文件，寻找此文件相关的块文件，找到最后一个块文件，由此块文件的序号决定续传位置。为简化处理，因为块本身最大10MB，不再考虑最后一个块文件的大小，而直接告诉客户端从头发送此块。

# 关于文件块
块文件的名称规则为 {fileName}.chunk{序号}，序号为4位，依次递增。服务器每接收一个新的文件块，就会新建一个上述规则的块文件。
当服务器端一个块文件接收完成后，校验MD5摘要，如果校验通过，则将块文件追加到主文件。如果校验失败，则告诉客户端重发此块。在实际接收时如果块校验通过并且成功追加到主文件后，此块文件按理可以清除以节省磁盘空间，但前期可以不清楚，以方便调试和定位问题。
