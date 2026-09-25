import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Card, Typography, Button, Spin, Alert, Result } from 'antd'
import { ArrowLeftOutlined, LockOutlined } from '@ant-design/icons'
import { columnApi } from '../api/column'
import type { Article } from '../types'
import dayjs from 'dayjs'

const { Title, Text, Paragraph } = Typography

function ArticleDetail() {
  const { id, articleId } = useParams<{ id: string; articleId: string }>()
  const navigate = useNavigate()
  const [article, setArticle] = useState<Article | null>(null)
  const [loading, setLoading] = useState(false)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    const loadArticle = async () => {
      if (!id || !articleId) return
      setLoading(true)
      try {
        const res = await columnApi.getArticle(id, articleId)
        const body = res.data
        if (body?.success === false || !body?.data) {
          setNotFound(true)
          return
        }
        setArticle(body.data)
      } catch (error) {
        console.error('Failed to load article:', error)
        setNotFound(true)
      } finally {
        setLoading(false)
      }
    }
    loadArticle()
  }, [id, articleId])

  if (loading) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  if (notFound || !article) {
    return (
      <Result
        status="404"
        title="文章不存在"
        extra={
          <Button type="primary" onClick={() => navigate(`/columns/${id}`)}>
            返回专栏
          </Button>
        }
      />
    )
  }

  const readable = article.readable === true

  return (
    <div>
      <Button
        icon={<ArrowLeftOutlined />}
        style={{ marginBottom: 16 }}
        onClick={() => navigate(`/columns/${id}`)}
      >
        返回专栏
      </Button>
      <Card>
        <Title level={2}>{article.title}</Title>
        <div style={{ marginBottom: 24 }}>
          {article.createdAt && (
            <Text type="secondary">
              发布于 {dayjs(article.createdAt).format('YYYY-MM-DD HH:mm')}
            </Text>
          )}
        </div>
        {readable ? (
          <Paragraph style={{ whiteSpace: 'pre-wrap', fontSize: 16, lineHeight: 1.9 }}>
            {article.content}
          </Paragraph>
        ) : (
          <div>
            <Paragraph type="secondary" style={{ whiteSpace: 'pre-wrap', fontSize: 16 }}>
              {article.summary || '本文暂无摘要'}
            </Paragraph>
            <Alert
              type="warning"
              showIcon
              icon={<LockOutlined />}
              message="本文需要订阅后阅读"
              description="您的订阅未覆盖本文的发布时间。续费专栏后即可阅读全文，到期前已发布的文章不受续费影响，仍可随时阅读。"
              action={
                <Button
                  type="primary"
                  onClick={() => navigate(`/columns/${id}?renew=1`)}
                >
                  去续费
                </Button>
              }
            />
          </div>
        )}
      </Card>
    </div>
  )
}

export default ArticleDetail
