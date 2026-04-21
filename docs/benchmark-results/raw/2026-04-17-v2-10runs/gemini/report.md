# AI Benchmark Report — gemini

- Cases: 30, Total runs: 300, Exceptions: 33

## Overall

| Metric | Value |
|---|---|
| Strict PASS rate | 59.0%  (95% CI 53.4% – 64.4%) |
| Mean Score | 91.4 / 100 |
| Mean IoU | 0.465 |
| pass@1 | 0.590 |
| pass@3 | 0.768 |
| pass^3 | 0.426 |

## By Category

| Bucket | Cases | Runs | Strict | Score | IoU |
|---|---:|---:|---:|---:|---:|
| ELECTRONICS | 5 | 50 | 46.0% | 86.1 | 0.587 |
| SPORTS | 5 | 50 | 58.0% | 98.7 | 0.447 |
| HOBBY | 5 | 50 | 46.0% | 92.0 | 0.306 |
| FASHION | 5 | 50 | 72.0% | 92.5 | 0.508 |
| OTHER | 5 | 50 | 56.0% | 88.6 | 0.367 |
| HOME | 5 | 50 | 76.0% | 90.8 | 0.532 |

## By Tag

| Bucket | Cases | Runs | Strict | Score | IoU |
|---|---:|---:|---:|---:|---:|
| vintage_premium | 6 | 60 | 45.0% | 91.1 | 0.380 |
| discontinued | 2 | 20 | 10.0% | 91.0 | 0.115 |
| boundary_price | 1 | 10 | 100.0% | 100.0 | 0.549 |
| low_price | 4 | 40 | 65.0% | 87.0 | 0.445 |
| quantity_ambiguous | 2 | 20 | 35.0% | 86.8 | 0.309 |
| high_value | 2 | 20 | 45.0% | 83.2 | 0.542 |
| brand_ambiguous | 1 | 10 | 0.0% | 0.0 | 0.000 |
| low_search_volume | 3 | 30 | 30.0% | 81.5 | 0.542 |
| overseas_niche | 1 | 10 | 0.0% | 64.5 | 0.000 |

## Bottom 3 Cases

| Case | Category | Runs | Strict | Score | IoU |
|---|---|---:|---:|---:|---:|
| fender-strat-am-std-b | HOBBY | 10 | 0.0% | 0.0 | 0.000 |
| macbook-pro-14-m3-b | ELECTRONICS | 10 | 20.0% | 55.2 | 0.431 |
| stanley-quencher-40oz-b | OTHER | 10 | 30.0% | 59.6 | 0.381 |

## Exceptions

| Case | Run | Type | Message |
|---|---:|---|---|
| iphone-15-pro-b | 1 | AiGenerationFailedException | 제공해주신 이미지는 여러 아이폰 모델을 보여주고 있어, 말씀하신 '아이폰 15 프로 256기가 블루티타늄'과 정확히 일치하는 상품을 판정하기 어렵습니다. 판매하시려는 상품의 실제 사진을 한 장만 다시 올려주시면 감사하겠습니다. |
| iphone-15-pro-b | 4 | AiGenerationFailedException | 이미지에 여러 개의 휴대전화가 보여서 어떤 상품에 대한 감정인지 명확히 알 수 없습니다. 판매하시려는 아이폰 15 Pro 256GB 블루 티타늄 한 개의 명확한 사진을 다시 올려주시면 정확하게 감정해 드리겠습니다. |
| galaxy-buds-3-pro-a | 2 | AiGenerationFailedException | 제공된 이미지의 상품과 입력하신 '갤럭시 버즈3 프로' 정보가 서로 다릅니다. 이미지에는 투명한 뚜껑을 가진 이어버드가 보이며, 이는 삼성 갤럭시 버즈 제품의 디자인과 차이가 있습니다. 다시 확인해 주시면 정확한 감정에 도움을 드릴 수 있습니다. |
| galaxy-buds-3-pro-a | 3 | AiGenerationFailedException | 입력해주신 '갤럭시 버즈3 프로' 정보와 이미지 속 상품의 외형이 다릅니다. 정확한 상품명이나 다른 이미지를 다시 확인해주시면 감사하겠습니다. |
| galaxy-buds-3-pro-a | 4 | AiGenerationFailedException | 사용자님, 이미지 속 제품은 '갤럭시 버즈3 프로'와 다른 디자인으로 보입니다. 제품의 실제 모델명을 다시 확인하여 알려주시거나, 보다 선명한 제품 사진을 첨부해 주시면 정확한 감정에 도움이 됩니다. |
| galaxy-buds-3-pro-a | 5 | AiGenerationFailedException | 이미지 속 상품과 말씀해주신 '갤럭시 버즈3 프로'는 디자인이 다릅니다. 정확한 상품명이나 상품 이미지를 다시 확인해 주시면 감정해 드릴 수 있습니다. |
| galaxy-buds-3-pro-a | 6 | AiGenerationFailedException | 제공해주신 이미지의 상품과 '갤럭시 버즈3 프로'라는 설명이 서로 달라 정확한 상품을 판별하기 어렵습니다. 상품명 또는 이미지를 다시 확인해 주시거나, 정확한 상품 정보를 알려주세요. |
| galaxy-buds-3-pro-a | 7 | AiGenerationFailedException | 이미지 속 상품은 갤럭시 버즈와 다른 디자인으로 보입니다. 이미지와 일치하는 상품명을 알려주시거나, 감정을 원하시는 상품의 정확한 이미지를 다시 올려주시면 감사하겠습니다. |
| galaxy-buds-3-pro-a | 8 | AiGenerationFailedException | 제공해주신 이미지와 '갤럭시 버즈3 프로' 정보가 서로 달라 상품을 정확히 식별하기 어렵습니다. 실제 제품명이나 제품 사진을 다시 확인하여 알려주시면 감사하겠습니다. |
| galaxy-buds-3-pro-a | 9 | AiGenerationFailedException | 제공해주신 '갤럭시 버즈3 프로'는 현재 출시되지 않은 모델로 확인됩니다. 정확한 제품명과 함께 제품의 모델명을 확인할 수 있는 사진을 다시 첨부해 주시면 감사하겠습니다. |
| galaxy-buds-3-pro-a | 10 | AiGenerationFailedException | 이미지 속 상품은 '갤럭시 버즈3 프로'와 일치하지 않는 것으로 보입니다. 모델명 확인 후 다시 시도하시거나, 해당 모델의 정확한 이미지를 제공해 주시면 감사하겠습니다. |
| taylormade-stealth2-driver-b | 1 | AiGenerationFailedException | 상품 이미지에 표시된 로프트는 9.0도인데, 입력하신 정보에는 10.5도로 기재되어 있습니다. 정확한 상품 식별을 위해 로프트 정보를 다시 확인해 주세요. |
| taylormade-stealth2-driver-b | 2 | AiGenerationFailedException | 이미지에 표시된 드라이버 로프트는 9.0도로 확인되지만, 입력하신 정보는 10.5도입니다. 어떤 정보가 맞는지 다시 확인하여 알려주세요. |
| taylormade-stealth2-driver-b | 3 | AiGenerationFailedException | 이미지에는 9.0도 드라이버로 보이는데, 입력하신 정보에는 10.5도로 되어 있습니다. 상품 정보를 다시 한번 확인해 주시겠어요? |
| taylormade-stealth2-driver-b | 4 | AiGenerationFailedException | 이미지에서 확인된 드라이버 로프트 각도는 9.0도인데, 고객님께서 10.5도로 기재해주셨습니다. 정확한 로프트 각도를 다시 한번 확인해주시면 감사하겠습니다. |
| taylormade-stealth2-driver-b | 5 | AiGenerationFailedException | 이미지에서 확인된 드라이버 헤드의 로프트 각도는 9.0도인데, 입력해주신 정보에는 10.5도로 기재되어 있습니다. 정확한 로프트 각도를 확인해 다시 알려주시면 감사하겠습니다. |
| taylormade-stealth2-driver-b | 7 | AiGenerationFailedException | 이미지에 보이는 드라이버 헤드의 로프트 각도가 9.0도로 확인됩니다. 입력해주신 정보(10.5도)와 차이가 있어 정확한 감정이 어렵습니다. 다시 한번 확인해주시면 감사하겠습니다. |
| taylormade-stealth2-driver-b | 8 | AiGenerationFailedException | 제공해주신 이미지와 입력하신 상품 정보가 일치하지 않습니다. 이미지에는 테일러메이드 스텔스2 플러스 9.0도 드라이버로 확인되나, 입력하신 정보는 스텔스2 10.5도 드라이버입니다. 정확한 상품 정보를 다시 알려주시면 감사하겠습니다. |
| taylormade-stealth2-driver-b | 9 | AiGenerationFailedException | 이미지에서 확인되는 상품은 테일러메이드 스텔스2 플러스 9도 드라이버입니다. 사용자 정보와 모델명 또는 로프트 각도가 일치하지 않아 정확한 상품 식별이 어렵습니다. 어떤 정보가 정확한지 확인해 주시면 감사하겠습니다. |
| taylormade-stealth2-driver-b | 10 | AiGenerationFailedException | 제공해주신 이미지에는 드라이버 로프트가 9.0도로 표시되어 있으나, 설명에는 10.5도로 기재되어 있습니다. 정확한 상품 식별을 위해 이미지와 설명 중 어느 정보가 맞는 것인지 확인 부탁드립니다. |
| fender-strat-am-std-b | 1 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 2 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 3 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 4 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 5 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 6 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 7 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 8 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 9 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| fender-strat-am-std-b | 10 | InvalidImageException | 이미지를 분석할 수 없어요. 다른 이미지로 다시 시도해주세요. |
| nike-zoomx-vaporfly-3-a | 4 | AiGenerationFailedException | 제공해주신 사진에서 신발이 절단된 상태로 보입니다. 중고 상품으로 감정하기는 어렵습니다. 실제 판매하시려는 상품의 상태를 보여주는 사진을 다시 올려주시면 감사하겠습니다. |
| nike-zoomx-vaporfly-3-a | 9 | AiGenerationFailedException | 이미지가 상품의 온전한 모습을 보여주지 않아 상태를 정확히 감정하기 어렵습니다. 판매하시려는 상품의 전체적인 모습과 상태가 잘 보이는 사진을 다시 올려주시면 감사하겠습니다. |
| stanley-quencher-40oz-b | 5 | AiGenerationFailedException | 이미지에는 새 상품이 진열되어 있지만, 고객님께서는 사용감 있는 중고 상품이라고 말씀해주셨습니다. 사진 속 상품이 중고 상품이 맞는지, 혹은 다른 상품을 분석해야 하는지 알려주시면 정확한 감정이 가능합니다. |
