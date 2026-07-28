import request from './index'

/**
 * 上传图片
 * @param {File} file - 图片文件
 * @returns {Promise} 返回图片URL
 */
export function uploadImageApi(file) {
  const formData = new FormData()
  formData.append('image', file)

  return request({
    url: '/upload/',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
