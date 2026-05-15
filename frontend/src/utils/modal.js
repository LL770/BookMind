// 全局弹窗状态（替代浏览器 alert/confirm）
import { ref } from 'vue'

export const modalState = ref({
  show: false,
  title: '',
  message: '',
  icon: '',
  type: 'info', // info | confirm
  confirmText: '确定',
  cancelText: '取消',
  onConfirm: null,
  onCancel: null,
})

export function showAlert(title, message, icon = '📢') {
  return new Promise((resolve) => {
    modalState.value = {
      show: true,
      title,
      message,
      icon,
      type: 'info',
      confirmText: '确定',
      onConfirm: () => { modalState.value.show = false; resolve(true) },
      onCancel: null,
    }
  })
}

export function showConfirm(title, message, icon = '⚠️') {
  return new Promise((resolve) => {
    modalState.value = {
      show: true,
      title,
      message,
      icon,
      type: 'confirm',
      confirmText: '确定',
      cancelText: '取消',
      onConfirm: () => { modalState.value.show = false; resolve(true) },
      onCancel: () => { modalState.value.show = false; resolve(false) },
    }
  })
}

export function closeModal() {
  modalState.value.show = false
}
