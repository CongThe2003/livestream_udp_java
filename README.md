1.	Cấu hình JAVACV:
$Env:JAVACV_HOME = "C:\Program Files\Java\javacv"

2.	Kiểm tra cấu hình JAVACV:
echo $Env:JAVACV_HOME

3.	Cấu hình JAVAFX:
$Env:JAVAFX_HOME = "C:\Program Files\Java\javafx-sdk-17.0.13"

4.	Kiểm tra cấu hình JAVAFX:
echo $Env:JAVAFX_HOME

5.	Cấu hình PATH cho cả JAVAFX và JAVACV: 
$Env:PATH = "$Env:JAVAFX_HOME\lib;$Env:JAVACV_HOME\*;$Env:PATH"

6.	Biên dịch UDPSender:
javac -cp "$Env:JAVACV_HOME\*;$Env:JAVAFX_HOME\lib\*" UDPSender.java  

Chạy UDPSender:
java -cp ".;$Env:JAVACV_HOME\*;$Env:JAVAFX_HOME\lib\*" UDPSender

7.	Biên dịch UDPReceiver:
javac -cp "$Env:JAVACV_HOME\*;$Env:JAVAFX_HOME\lib\*" UDPReceiver.java

Chạy UDPReceiver:
java -cp ".;$Env:JAVACV_HOME\*;$Env:JAVAFX_HOME\lib\*" UDPReceiver
