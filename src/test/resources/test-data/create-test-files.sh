#!/bin/bash
# 创建测试图片
convert -size 100x100 xc:blue src/test/resources/test-data/test.jpg 2>/dev/null || \
java -cp ".:*" -Djava.awt.headless=true TestImageCreator

# 创建简单的测试PDF（使用文本作为占位）
echo "Test PDF content" > src/test/resources/test-data/test.txt
