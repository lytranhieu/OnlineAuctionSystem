# Khởi tạo dự án và nghiệm thu nền tảng OOP

## Mục tiêu

- [ ] Thảo luận phạm vi, phân công bốn thành viên
- [ ] Tạo repository; tạo nhánh `main` và `dev` trên GitHub
- [x] Tạo Maven parent và 4 module đúng cấu trúc hiện tại của dự án
- [x] Có `Entity`, `User`, `Bidder`, `Seller`, `Admin` và `Item`, `Electronics`, `Art`, `Vehicle`
- [x] Có `Auction`, `BidTransaction` và dữ liệu enum
- [x] Có `ItemFactory` (Factory Method), `AuctionManager` (Singleton)
- [x] Có mã nguồn và FXML Hello World JavaFX
- [ ] Cả nhóm tự chạy Maven/JavaFX trên máy và chụp kết quả
- [ ] Các thành viên đưa phần thực sự tự làm lên các nhánh Git và mở Pull Request

Các ô GitHub, demo JavaFX là **việc cần nhóm thực hiện**, không được coi là đã nghiệm thu chỉ vì ZIP có tệp.

## Lệnh Git cho repository mới hoàn toàn trống

```bash
git init
git branch -M main
git add .
git commit -m "chore: set up online auction project structure"
git remote add origin <URL_REPOSITORY_GITHUB_CUA_NHOM>
git push -u origin main
git switch -c dev
git push -u origin dev
```

Nếu GitHub đã có commit thì **không chạy lại** chuỗi này: clone và đồng bộ lịch sử hiện tại trước.
