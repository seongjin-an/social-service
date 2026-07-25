## 로컬 환경에서 프로파일 이미지 저장
```
# 요청 시.
POST http://{{domain}}:{{port}}/api/profiles/{{profileId}}/images
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJqdGkiOiJkYTY0YWRkYi04MzU4LTQwY2ItODRmYy00Zjk0NjVmOGFlNTQiLCJzdWIiOiI3NGI5YWYwNy03NjI2LTQ3ZWQtOWQzMy1lZGVmYWJlYmM4NGEiLCJyb2xlIjoiVVNFUiIsIm5hbWUiOiLthYzsiqTtirjsnKDsoIAiLCJpYXQiOjE3ODQ5Nzk5ODUsImV4cCI6MTc4NDk4MzU4NX0.hxQE6QPcfyiAB94RIj9nnmIIQsepMSR6KOpdrlaQmry6tpp0-SEMwY5COpYKk2foXm8EddlsL2T9nwVh_DdsKw

[
  {
    "imageId": "019f9919-ffd0-7dd3-b913-bbe80c3e8841",
    "imageUrl": "http://localhost:8085/files/profile/019f9919-ffcd-7e6b-b44c-c8dec96de0c8.jpg",
    "primaryImage": true
  }
]

# 디비에서 저장된 데이터
[
  {
    "profile_image_id": "0x019F9919FFD07DD3B913BBE80C3E8841",
    "created_at": "2026-07-25 20:47:25.789159",
    "updated_at": "2026-07-25 20:47:25.789159",
    "content_type": "image/jpeg",
    "file_size": 11992,
    "image_url": "http://localhost:8085/files/profile/019f9919-ffcd-7e6b-b44c-c8dec96de0c8.jpg",
    "object_key": "profile/019f9919-ffcd-7e6b-b44c-c8dec96de0c8.jpg",
    "original_file_name": "profile1.jpg",
    "primary_image": true,
    "stored_file_name": "019f9919-ffcd-7e6b-b44c-c8dec96de0c8.jpg",
    "profile_id": "0x019F79878C2573FD9509BBB6E18C2E5F"
  }
]
```